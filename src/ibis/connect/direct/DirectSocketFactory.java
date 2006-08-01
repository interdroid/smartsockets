package ibis.connect.direct;

import ibis.connect.util.NetworkUtils;
import ibis.connect.util.STUN;
import ibis.connect.util.UPNP;
import ibis.util.TypedProperties;

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
import java.util.StringTokenizer;

import org.apache.log4j.Logger;

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

    private static final int TIMEOUT = 5000;

    private static final boolean USE_NIO = TypedProperties.booleanProperty(
            Properties.USE_NIO, false);

    private static final boolean ALLOW_STUN = TypedProperties.booleanProperty(
            Properties.USE_STUN, true);
    
    private static final boolean ALLOW_UPNP = TypedProperties.booleanProperty(
            Properties.USE_UPNP, false);

    private static final boolean ALLOW_BOUNCER = TypedProperties
            .booleanProperty(Properties.USE_BOUNCER, false);

    protected static Logger logger = ibis.util.GetLogger
            .getLogger(DirectSocketFactory.class.getName());

    private static final DirectSocketFactory factory = new DirectSocketFactory();

    private IPAddressSet completeAddress;

    private IPAddressSet localAddress;

    private InetAddress externalNATAddress;

    private boolean haveOnlyLocalAddresses = false;

    private String myNATAddress;

    private PortRange portRange;

    private NetworkPreference preference;

    private DirectSocketFactory() {

        localAddress = IPAddressSet.getLocalHost();

        if (!localAddress.containsGlobalAddress()) {
            haveOnlyLocalAddresses = true;

            getNATAddress();
            getExternalAddress();

            if (externalNATAddress != null) {
                completeAddress = IPAddressSet.merge(localAddress,
                        externalNATAddress);
            } else {
                completeAddress = localAddress;
            }
        } else {
            completeAddress = localAddress;
        }

        portRange = new PortRange();

        preference = new NetworkPreference(completeAddress);
        preference.sort(completeAddress.getAddresses(), true);

        // if (logger.isDebugEnabled()) {
        logger.info("Local address: " + completeAddress);
        // }
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
    private void getExternalAddress() {

        // Check if externalAddress is already known
        if (externalNATAddress != null) {
            return;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Checking properties for external address...");
        }

        externalNATAddress = getExternalAddressProperty();

        if (logger.isDebugEnabled()) {
            logger.debug("Properties lookup result: " + externalNATAddress);
        }

        if (externalNATAddress != null) {
            return;
        }

        if (ALLOW_STUN) { 
            if (logger.isDebugEnabled()) {
                logger.debug("Using STUN to find external address...");
            }
   
            externalNATAddress = STUN.getExternalAddress(null);
            
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

        if (externalNATAddress != null) {
            return;
        }

        // TODO: remove ? Replaced by STUN....
        if (ALLOW_BOUNCER) {
            // Try to obtain the global IP address by using an external bouncer.
            if (logger.isDebugEnabled()) {
                logger.debug("Using BOUNCER to find external address...");
            }

            SocketAddressSet[] bouncers = getBouncerProperty();

            for (int i = 0; i < bouncers.length; i++) {
                if (bouncers[i] != null) {
                    externalNATAddress = contactBouncer(bouncers[i], 1500);

                    if (externalNATAddress != null) {
                        break;
                    }
                }
            }

            if (logger.isDebugEnabled()) {
                logger.debug("BOUNCER lookup result: " + externalNATAddress);
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
    private InetAddress getExternalAddressProperty() {

        InetAddress result = null;

        String tmp = TypedProperties
                .stringProperty(Properties.EXTERNAL_ADDR);

        if (tmp != null) {
            try {
                result = InetAddress.getByName(tmp);
            } catch (UnknownHostException e) {
                logger.warn("Failed to parse property \""
                        + Properties.EXTERNAL_ADDR + "\"");
            }
        }

        return result;
    }

    /**
     * This method retrieves the 'ibis.connect.bouncers' property, which
     * contains a comma seperates list of bouncer addresses. Each address is
     * expected to be is the InetSetAddress or InetSetSockerAddress format:
     * 
     * IP1/IP2/.../IPx
     * 
     * or
     * 
     * IP1/IP2/.../IPx:PORT
     * 
     * A array of InetSetSocketAddress object will be returned. Note that this
     * array may contain null values if a certain address could not be created.
     * 
     * When the property is not found, the Bouncer.DEFAULT_BOUNCER and
     * Bouncer.DEFAULT_PORT will be used instead.
     * 
     * @return array of InetSetSocketAddresses
     */
    private SocketAddressSet[] getBouncerProperty() {

        String tmp = TypedProperties.stringProperty(
                Properties.BOUNCERS, Bouncer.DEFAULT_BOUNCER);

        StringTokenizer t = new StringTokenizer(tmp, ",");

        int count = t.countTokens();
        SocketAddressSet[] res = new SocketAddressSet[count];

        count = 0;

        while (t.hasMoreTokens()) {
            try {
                String b = t.nextToken();

                if (b.lastIndexOf(':') >= 0) {
                    // address contains a port
                    res[count++] = new SocketAddressSet(b);
                } else {
                    // adress does not contain a port, so use default
                    res[count++] = new SocketAddressSet(b, Bouncer.DEFAULT_PORT);
                }
            } catch (UnknownHostException e) {
                logger.warn("Failed to create BOUNCER address" + e);
            }
        }

        return res;
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

    /*
     * private Socket createBoundSocket() throws IOException {
     * 
     * Socket s = createUnboundSocket();
     * 
     * int port = portRange.getPort();
     * 
     * if (port == 0) { // We can use any port we like. If it fails, there's
     * nothing we // can do about it. s.bind(null); return s; }
     *  // We must use a specfic port range. Some of the ports in the range may //
     * already be taken, so it may take a few tries before we find one that //
     * is available.
     *  // TODO: add timeout/max tries ?? while (true) { try { s.bind(new
     * InetSocketAddress(port)); return s; } catch (BindException e) { // port
     * in use, so get another one and try again. port = portRange.getPort(); } } }
     */

    private ServerSocket createUnboundServerSocket() throws IOException {
        if (USE_NIO) {
            ServerSocketChannel channel = ServerSocketChannel.open();
            return channel.socket();
        } else {
            return new ServerSocket();
        }
    }

    private InetAddress contactBouncer(SocketAddressSet bouncer, int timeout) {

        long start = System.currentTimeMillis();
        long end = start;

        while (timeout == 0 || ((end - start) < timeout)) {

            DirectSocket s = null;

            try {
                s = createSocket(bouncer, timeout, null);
                s.setSoTimeout(1000);

                InputStream in = s.getInputStream();

                byte[] address = new byte[16];

                int index = 0;
                boolean done = false;

                while (!done) {
                    int data = in.read();

                    if (data == -1) {
                        done = true;
                    } else {
                        address[index++] = (byte) data;
                    }
                }

                in.close();

                if (index == 4) {
                    byte[] tmp = new byte[4];
                    System.arraycopy(address, 0, tmp, 0, 4);
                    address = tmp;
                }

                return InetAddress.getByAddress(address);

            } catch (Exception e) {
                logger.warn("Failed to contact Bouncer " + e);

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    // ignore
                }

            } finally {
                try {
                    s.close();
                } catch (Throwable e) {
                    // ignore
                }
            }

            end = System.currentTimeMillis();
        }

        return null;
    }

    private DirectSocket attemptConnection(SocketAddressSet sas,
            InetSocketAddress target, int timeout, int localPort, 
            boolean mayBlock) {

        // We never want to block, so ensure that timeout > 0
        if (timeout == 0 && !mayBlock) {
            timeout = TIMEOUT;
        }

        Socket s = null;

        try {

            logger.info("Attempting connection to " + sas.toString()
                    + " using network "
                    + NetworkUtils.ipToString(target.getAddress()) + ":"
                    + target.getPort());

            s = createUnboundSocket();

            logger.info("Unbound socket created");

            if (localPort > 0) {                 
                s.bind(new InetSocketAddress(localPort));
            }
                
            s.connect(target, timeout);

            // if (logger.isDebugEnabled()) {
            logger.info("Succesfully connected to " + sas.toString()
                    + " using network "
                    + NetworkUtils.ipToString(target.getAddress()) + ":"
                    + target.getPort());
            // }

            // TODO: verify that we are actually talking to the right machine ?
            DirectSocket r = new DirectSocket(s);
            tuneSocket(r);
            return r;

        } catch (Throwable e) {

            // if (logger.isDebugEnabled()) {
            logger.info("Failed to connect to "
                    + NetworkUtils.ipToString(target.getAddress()) + ":"
                    + target.getPort(), e);
            // }

            try {
                s.close();
            } catch (Exception e2) {
                // ignore
            }

            return null;
        }
    }

    /*
     * private PlainSocket attemptConnection(InetAddress[] from, InetAddress to,
     * int port, boolean justTry) {
     * 
     * for (int i=0;i<from.length;i++) { PlainSocket result =
     * attemptConnection(from[i], to, port, justTry);
     * 
     * if (result != null) { return result; } }
     * 
     * return null; }
     * 
     * private PlainSocket connect(IbisSocketAddress target) throws IOException {
     * 
     * InetAddress [] src = localAddress.getAddresses(); SocketAddress [] sas =
     * target.getSocketAddresses();
     * 
     * for (int i=0;i<sas.length;i++) { InetSocketAddress sa =
     * (InetSocketAddress)sas[i]; PlainSocket result = attemptConnection(src,
     * sa.getAddress(), sa.getPort(), false);
     * 
     * if (result != null) { return result; } }
     * 
     * throw new ConnectException("Connection refused"); }
     */

    private DirectServerSocket createServerSocket(int port, int backlog,
            boolean portForwarding, boolean forwardingMayFail,
            boolean sameExternalPort) throws IOException {

        if (port == 0) {
            port = portRange.getPort();
        }

        ServerSocket ss = createUnboundServerSocket();
        ss.bind(new InetSocketAddress(port), backlog);

        SocketAddressSet local = new SocketAddressSet(completeAddress, ss
                .getLocalPort());

        DirectServerSocket smss = new DirectServerSocket(local, ss);

        if (!haveOnlyLocalAddresses || !portForwarding) {
            // We are not behind a NAT box or the user doesn't want port
            // forwarding, so just return the server socket

            if (logger.isDebugEnabled()) {
                logger.debug("Created server socket on: " + smss);
            }

            return smss;
        }

        // We only have a local address, and the user wants to try PF.
        if (port == 0) {
            port = ss.getLocalPort();
        }

        try {

            int ePort = sameExternalPort ? port : 0;
            ePort = UPNP.addPortMapping(port, ePort, myNATAddress, 0, "TCP");
            smss.addExternalAddress(new SocketAddressSet(externalNATAddress,
                    ePort));

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
    protected static void tuneSocket(DirectSocket s) throws IOException {
        if (Properties.inputBufferSize != 0) {
            s.setReceiveBufferSize(Properties.inputBufferSize);
        }
        if (Properties.outputBufferSize != 0) {
            s.setSendBufferSize(Properties.outputBufferSize);
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
            timeout = TIMEOUT;
        }

        InetSocketAddress[] sas = target.getSocketAddresses();

        // Select the addresses we want from the target set. Some may be removed
        // thanks to the cluster configuration.
        sas = preference.sort(sas, false);

        if (sas.length == 1) {
            // only one socket left, so allow sleeping for ever...
            DirectSocket result = attemptConnection(target, sas[0], timeout,
                    localPort, true);

            if (result != null) {
                return result;
            }

            throw new ConnectException("Connection setup failed");
        }

        // else, we must try them all.
        while (true) {
            for (int i = 0; i < sas.length; i++) {
                InetSocketAddress sa = sas[i];
                DirectSocket result = attemptConnection(target, sa, timeout,
                        localPort, false);

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
     * Returns an instance of PlainSocketFactory.
     * 
     * @return PlainSocketFactory
     */
    public static DirectSocketFactory getSocketFactory() {
        return factory;
    }
}
