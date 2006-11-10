package smartsockets.direct;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Map;

import org.apache.log4j.Logger;

import smartsockets.Properties;
import smartsockets.util.NetworkUtils;
import smartsockets.util.STUN;
import smartsockets.util.TypedProperties;
import smartsockets.util.UPNP;

/**
 * This class implements a socket factory with support for multi-homes machines,
 * port ranges, external address discovery, UPNP port forwarding, NIO-style
 * socket creation, clustering and connection address order preference.
 * 
 * This socket factory always tries to set up a direct connection between
 * machines. As a result, there are many scenarios possible in which it will not
 * be able to connect. It is a good basis for other, 'smarter' socket factories
 * however.
 * 
 * @author Jason Maassen
 * @version 1.0 Jan 10, 2006
 * @since 1.0
 */
public class DirectSocketFactory {

    protected static Logger logger = ibis.util.GetLogger
        .getLogger("smartsockets.direct");

    private static DirectSocketFactory defaultFactory; 
    
    private final int DEFAULT_TIMEOUT;
    
    private final TypedProperties properties;
    
    private final boolean USE_NIO;
    
    private final boolean ALLOW_UPNP;
    private final boolean ALLOW_UPNP_PORT_FORWARDING;    
    
    private final int inputBufferSize;
    private final int outputBufferSize;
        
    private IPAddressSet completeAddress;

    private IPAddressSet localAddress;

    private InetAddress externalNATAddress;

    private boolean haveOnlyLocalAddresses = false;

    private String myNATAddress;

    private PortRange portRange;

    private NetworkPreference preference;

    private DirectSocketFactory(TypedProperties p) {
        
        properties = p;
    
        DEFAULT_TIMEOUT = p.getIntProperty(Properties.TIMEOUT, 5000);
        ALLOW_UPNP = p.booleanProperty(Properties.UPNP, false);
        
        if (!ALLOW_UPNP) { 
            ALLOW_UPNP_PORT_FORWARDING = false;
        } else { 
            ALLOW_UPNP_PORT_FORWARDING = 
                p.booleanProperty(Properties.UPNP_PORT_FORWARDING, false);
        }
        
        USE_NIO = p.booleanProperty(Properties.NIO, false);
        
        inputBufferSize = p.getIntProperty(Properties.IN_BUF_SIZE, 0);
        outputBufferSize = p.getIntProperty(Properties.OUT_BUF_SIZE, 0);
                        
        localAddress = IPAddressSet.getLocalHost();

        if (!localAddress.containsGlobalAddress()) {
            haveOnlyLocalAddresses = true;

            getExternalAddress(p);

            if (externalNATAddress != null) {
                completeAddress = IPAddressSet.merge(localAddress,
                        externalNATAddress);
            } else {
                completeAddress = localAddress;
            }
        } else {
            completeAddress = localAddress;
        }

        portRange = new PortRange(p);

        preference = NetworkPreference.getPreference(completeAddress, p);
        preference.sort(completeAddress.getAddresses(), true);

        if (logger.isDebugEnabled()) {
            logger.info("Local address: " + completeAddress);
        }
    }

    private void applyMask(byte[] mask, byte[] address) {
        for (int i = 0; i < address.length; i++) {
            address[i] &= mask[i];
        }
    }

    /**
     * This method tries to find which of the local addresses is part of the NAT
     * network. Useful when multiple local networks exist.
     */
    private void getNATAddress() {
        if (ALLOW_UPNP) {
            if (logger.isDebugEnabled()) {
                logger.debug("Using UPNP to find my NAT'ed network");
            }

            // First get the netmask and an address in the range returned by
            // the NAT box.
            byte[] mask = UPNP.getSubnetMask();
            InetAddress[] range = UPNP.getAddressRange();

            if (mask == null || range == null) {
                return;
            }

            // Get a local address from the NAT box (not necc. our own).
            byte[] nw = range[0].getAddress();

            if (mask.length != nw.length) {
                return;
            }

            // Determine the network address.
            applyMask(mask, nw);

            // Now compare all local addresses to the network address.
            InetAddress[] ads = localAddress.getAddresses();

            for (int i = 0; i < ads.length; i++) {
                byte[] tmp = ads[i].getAddress();

                if (tmp.length == mask.length) {
                    applyMask(mask, tmp);

                    if (Arrays.equals(nw, tmp)) {
                        // Found and address that matches, so remember it...
                        myNATAddress = NetworkUtils.ipToString(ads[i]);
                        break;
                    }
                }
            }

            if (logger.isDebugEnabled()) {
                logger.debug("UPNP result: " + myNATAddress);
            }
        }
    }

    /**
     * This method tries to find a global address that is valid for this
     * machine. When an address is found, it it stored in the externalAddress
     * field.
     */
    private void getExternalAddress(TypedProperties p) {

        // Check if externalAddress is already known
        if (externalNATAddress != null) {
            return;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Checking properties for external address...");
        }

        externalNATAddress = getExternalAddressProperty(p);

        if (logger.isDebugEnabled()) {
            logger.debug("Properties lookup result: " + externalNATAddress);
        }

        if (externalNATAddress != null) {
            return;
        }

        if (p.booleanProperty(Properties.STUN, false)) {
            
            if (logger.isDebugEnabled()) {
                logger.debug("Using STUN to find external address...");
            }
   
            String [] servers = p.getStringList(Properties.STUN_SERVERS, ",", null); 
            
            externalNATAddress = STUN.getExternalAddress(servers);
            
            if (logger.isDebugEnabled()) {
                logger.debug("STUN lookup result: " + externalNATAddress);
            }
        } 
        
        if (ALLOW_UPNP) {
            // Try to obtain the global IP address by using UPNP.
            if (logger.isDebugEnabled()) {
                logger.debug("Using UPNP to find external address...");
            }

            externalNATAddress = UPNP.getExternalAddress();

            if (logger.isDebugEnabled()) {
                logger.debug("UPNP lookup result: " + externalNATAddress);
            }
        }
    }

    /**
     * This method retrieves the 'ibis.connect.external_address' property, which
     * contains a String representation of an InetAddress. If the property is
     * not set or the value could not be parsed, null is returned.
     * 
     * @return an InetAddress or null
     */
    private InetAddress getExternalAddressProperty(TypedProperties p) {

        InetAddress result = null;

        String tmp = p.getProperty(Properties.EXTERNAL_MANUAL);

        if (tmp != null) {
            try {
                result = InetAddress.getByName(tmp);
            } catch (UnknownHostException e) {
                logger.warn("Failed to parse property \""
                        + Properties.EXTERNAL_MANUAL + "\"");
            }
        }

        return result;
    }

    private Socket createUnboundSocket() throws IOException {

        Socket s = null;

        if (USE_NIO) {
            SocketChannel channel = SocketChannel.open();
            s = channel.socket();
        } else {
            s = new Socket();
        }

        s.setReuseAddress(true);
        return s;
    }

    private ServerSocket createUnboundServerSocket() throws IOException {
        if (USE_NIO) {
            ServerSocketChannel channel = ServerSocketChannel.open();
            return channel.socket();
        } else {
            return new ServerSocket();
        }
    }

    private DirectSocket attemptConnection(SocketAddressSet sas,
            InetSocketAddress target, int timeout, int localPort, 
            boolean mayBlock) {

        // We never want to block, so ensure that timeout > 0
        if (timeout == 0 && !mayBlock) {
            timeout = DEFAULT_TIMEOUT;
        }

        Socket s = null;

        try {
            if (logger.isInfoEnabled()) {
                logger.info("Attempting connection to " + sas.toString()
                        + " using network "
                        + NetworkUtils.ipToString(target.getAddress()) + ":"
                        + target.getPort());
            }
            
            s = createUnboundSocket();

            if (logger.isInfoEnabled()) {
                logger.info("Unbound socket created");
            }
            
            if (localPort > 0) {                 
                s.bind(new InetSocketAddress(localPort));
            }
                
            s.connect(target, timeout);

            if (logger.isInfoEnabled()) {
                logger.info("Succesfully connected to " + sas.toString()
                        + " using network "
                        + NetworkUtils.ipToString(target.getAddress()) + ":"
                        + target.getPort());
            }

            // TODO: verify that we are actually talking to the right machine ?
            DirectSocket r = new DirectSocket(s);
            tuneSocket(r);
            return r;

        } catch (Throwable e) {

            if (logger.isInfoEnabled()) {
                logger.info("Failed to connect to "
                        + NetworkUtils.ipToString(target.getAddress()) + ":"
                        + target.getPort(), e);
            }

            try {
                s.close();
            } catch (Exception e2) {
                // ignore
            }

            return null;
        }
    }
   
    private DirectServerSocket createServerSocket(int port, int backlog,
            boolean portForwarding, boolean forwardingMayFail,
            boolean sameExternalPort) throws IOException {

        if (port == 0) {
            port = portRange.getPort();
        }

        ServerSocket ss = createUnboundServerSocket();
        ss.bind(new InetSocketAddress(port), backlog);

        SocketAddressSet local = 
            new SocketAddressSet(completeAddress, ss.getLocalPort());

        DirectServerSocket smss = new DirectServerSocket(local, ss);
        
        if (!(haveOnlyLocalAddresses && portForwarding)) {
            // We are not behind a NAT box or the user doesn't want port
            // forwarding, so just return the server socket

            if (logger.isDebugEnabled()) {
                logger.debug("Created server socket on: " + smss);
            }

            return smss;
        }

        // We only have a local address, and the user wants to try port 
        // forwarding. Check if we are allowed to do so in the first place...
        
        
        if (!ALLOW_UPNP_PORT_FORWARDING) { 
            // We are not allowed to do port forwarding. Check if this is OK by
            // the user.
            
            if (forwardingMayFail) {
                // It's OK, so return the socket.
                if (logger.isDebugEnabled()) {
                    logger.debug("Port forwarding not allowed for: " + smss);
                }
                
                return smss;
            }

            // The user does not want the serversocket if it's not forwarded, so 
            // close it and throw an exception.

            logger.warn("Port not allowed for: " + smss);
            
            try {                
                ss.close();
            } catch (Throwable t) {
                // ignore
            }

            throw new IOException("Port forwarding not allowed!");
        }

        // Try to do port forwarding!        
        if (port == 0) {
            port = ss.getLocalPort();
        }

        try {
            int ePort = sameExternalPort ? port : 0;
            ePort = UPNP.addPortMapping(port, ePort, myNATAddress, 0, "TCP");
            smss.addExternalAddress(
                    new SocketAddressSet(externalNATAddress, ePort));

        } catch (Exception e) {

            logger.warn("Port forwarding failed for: " + local + " " + e);

            if (!forwardingMayFail) {
                // User doesn't want the port forwarding to fail, so close the
                // server socket and throw an exception.
                try {
                    ss.close();
                } catch (Throwable t) {
                    // ignore
                }

                throw new IOException("Port forwarding failed: " + e);
            }
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Created server socket on: " + smss);
        }

        return smss;
    }

    /**
     * Configures a socket according to user-specified properties. Currently,
     * the input buffer size and output buffer size can be set using the system
     * properties "ibis.util.socketfactory.InputBufferSize" and
     * "ibis.util.socketfactory.OutputBufferSize".
     * 
     * @param s
     *            the socket to be configured
     * 
     * @exception IOException
     *                when the configation has failed for some reason.
     */
    protected void tuneSocket(DirectSocket s) throws IOException {
        
        if (inputBufferSize != 0) {
            s.setReceiveBufferSize(inputBufferSize);
        }
        
        if (outputBufferSize != 0) {
            s.setSendBufferSize(outputBufferSize);
        }
        
        s.setTcpNoDelay(true);
    }

    /**
     * Retrieves a boolean property from a Map, using a given key.
     *  - If the map is null or the property does not exist, the default value
     * is returned. - If the property exists but has no value true is returned. -
     * If the property exists and has a value equal to "true", "yes", "on" or
     * "1", true is returned. - Otherwise, false is returned.
     * 
     * @param prop
     *            the map
     * @param key
     *            the name of the property
     * @param def
     *            the default value
     * @return boolean result
     */
    private boolean getProperty(Map prop, String key, boolean def) {

        if (prop != null && prop.containsKey(key)) {

            String value = (String) prop.get(key);

            if (value != null) {
                return value.equalsIgnoreCase("true")
                        || value.equalsIgnoreCase("on")
                        || value.equalsIgnoreCase("yes")
                        || value.equalsIgnoreCase("1");
            }

            return true;
        }
        return def;
    }

    public IPAddressSet getLocalAddress() {
        return completeAddress;
    }

    public static void close(DirectSocket s, OutputStream o, InputStream i) {
        
        if (o != null) { 
            try {
                o.close();
            } catch (Exception e) {
                // ignore
            }
        } 
        
        if (i != null) { 
            try { 
                i.close();
            } catch (Exception e) {
                // ignore
            }
        } 
        
        if (s != null) { 
            try { 
                s.close();
            } catch (Exception e) {
                // ignore
            }
        }        
    } 
    
    /*
     * (non-Javadoc)
     * 
     * @see smartnet.factories.ClientServerSocketFactory#createClientSocket(smartnet.IbisSocketAddress,
     *      int, java.util.Map)
     */
    public DirectSocket createSocket(SocketAddressSet target, int timeout,
            Map properties) throws IOException {
        return createSocket(target, timeout, 0, properties);
    }

    
    /*
     * (non-Javadoc)
     * 
     * @see smartnet.factories.ClientServerSocketFactory#createClientSocket(smartnet.IbisSocketAddress,
     *      int, java.util.Map)
     */
    public DirectSocket createSocket(SocketAddressSet target, int timeout,
            int localPort, Map properties) throws IOException {

        if (timeout < 0) {
            timeout = DEFAULT_TIMEOUT;
        }

        InetSocketAddress[] sas = target.getSocketAddresses();

        // Select the addresses we want from the target set. Some may be removed
        // thanks to the cluster configuration.
        sas = preference.sort(sas, false);

        if (sas.length == 1) {
            
            long time = System.currentTimeMillis();
            
            // only one socket left, so allow sleeping for ever...
            DirectSocket result = attemptConnection(target, sas[0], timeout,
                    localPort, true);

            time = System.currentTimeMillis() -time;
            
            if (logger.isInfoEnabled()) {              
                logger.info("Connection setup took: "  + time + " ms.");                
            }
            
            if (result != null) {
                return result;
            }

            throw new ConnectException("Connection setup failed");
        }

        // else, we must try them all, so the connection attempt must return at 
        // some point, even if timeout == 0
        while (true) {
            for (int i = 0; i < sas.length; i++) {
                
                long time = System.currentTimeMillis();
                               
                InetSocketAddress sa = sas[i];
                DirectSocket result = attemptConnection(target, sa, timeout,
                        localPort, false);

                time = System.currentTimeMillis() -time;
                
                if (logger.isInfoEnabled()) {                   
                    logger.info("Connection setup took: "  + time + " ms.");
                }
                                
                if (result != null) {
                    return result;
                }
            }

            if (timeout > 0) {
                // Enough tries so, throw exception
                throw new ConnectException("Connection setup failed");
            } // else, the user wants us to keep trying!
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see smartnet.factories.ClientServerSocketFactory#createServerSocket(int,
     *      int, java.util.Map)
     */
    public DirectServerSocket createServerSocket(int port, int backlog, Map prop)
            throws IOException {

        boolean forwardMayFail = true;
        boolean sameExternalPort = true;
        boolean portForwarding = getProperty(prop, "PortForwarding", false);

        if (portForwarding) {
            forwardMayFail = getProperty(prop, "ForwardingMayFail", true);
            sameExternalPort = getProperty(prop, "SameExternalPort", true);
        }

        return createServerSocket(port, backlog, portForwarding,
                forwardMayFail, sameExternalPort);
    }

    /*
     * public IbisSocket createBrokeredSocket(InputStream in, OutputStream out,
     * boolean hintIsServer, Map properties) throws IOException {
     * 
     * IbisSocket s = null;
     * 
     * if (hintIsServer) { IbisServerSocket server = createServerSocket(0, 1,
     * properties);
     * 
     * DataOutputStream dos = new DataOutputStream(new
     * BufferedOutputStream(out));
     * 
     * dos.writeUTF(server.getLocalSocketAddress().toString()); dos.flush();
     * 
     * s = server.accept(); tuneSocket(s);
     *  } else { DataInputStream di = new DataInputStream(new
     * BufferedInputStream(in));
     * 
     * String tmp = di.readUTF();
     * 
     * MachineAddress address = null;
     * 
     * try { address = new MachineAddress(tmp); } catch(Exception e) { throw new
     * Error("EEK, could not create an IbisSocketAddress " + " from " + tmp, e); }
     * 
     * s = createClientSocket(address, 0, properties); }
     * 
     * return s; }
     */
    
    /**
     * Returns if an address originated at this process.
     * 
     * @return boolean indicating if the address is local.
     */    
    public boolean isLocalAddress(IPAddressSet a) {        
        return (a.equals(completeAddress));
    }
        
    /**
     * Returns a custom instance of a DirectSocketFactory.
     * 
     * @return PlainSocketFactory
     */
    public static DirectSocketFactory getSocketFactory(TypedProperties p) {                
        return new DirectSocketFactory(p);
    }
    
    /**
     * Returns the default instance of a DirectSocketFactory.
     * 
     * @return PlainSocketFactory
     */
    public static DirectSocketFactory getSocketFactory() {
        
        if (defaultFactory == null) {             
            defaultFactory = 
                new DirectSocketFactory(Properties.getDefaultProperties());                         
        }

        return defaultFactory;
    }
    
    
}
