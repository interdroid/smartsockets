package smartsockets.direct;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Map;

import org.apache.log4j.Logger;

import ch.ethz.ssh2.Connection;
import ch.ethz.ssh2.LocalStreamForwarder;

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
    private final int DEFAULT_BACKLOG;
    
    private final TypedProperties properties;
    
    private final boolean USE_NIO;
    
    private final boolean ALLOW_UPNP;
    private final boolean ALLOW_UPNP_PORT_FORWARDING;        
    private final boolean ALLOW_SSH_OUT;    
        
    private final int inputBufferSize;
    private final int outputBufferSize;
        
    // User for SSH tunneling
    private final String user;
    private final char [] privateKey;
    
    private IPAddressSet localAddress;
    private IPAddressSet externalAddress;  
    private IPAddressSet completeAddress;
    
    private byte [] completeAddressInBytes;
    private byte [] networkNameInBytes;
    
    private InetAddress externalNATAddress;

    private boolean haveOnlyLocalAddresses = false;

    private String myNATAddress;

    private PortRange portRange;

    private NetworkPreference preference;

    private String keyFilePass = "";

    private DirectSocketFactory(TypedProperties p) {
        
        properties = p;
    
        DEFAULT_BACKLOG = p.getIntProperty(Properties.DIRECT_BACKLOG, 100);
        DEFAULT_TIMEOUT = p.getIntProperty(Properties.TIMEOUT, 5000);
        
        boolean allowSSHIn = p.booleanProperty(Properties.SSH_IN, false);

        if (allowSSHIn) { 
            user = System.getProperty("user.name");
        } else { 
            user = null;
        }
        
        boolean allowSSHOut = p.booleanProperty(Properties.SSH_OUT, false);
        
        if (allowSSHOut) {
            privateKey = getPrivateSSHKey();
        } else { 
            privateKey = null;
        }
        
        ALLOW_SSH_OUT = (privateKey != null);
        
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

        if (logger.isInfoEnabled()) {
            logger.info("Local address: " + completeAddress);
            logger.info("Local network: " + 
                 (preference == null ? "<none>" : preference.getNetworkName()));
        }
        
        completeAddressInBytes = toBytes(completeAddress);
        networkNameInBytes = toBytes(
                preference == null ? null : preference.getNetworkName());
    }

    private char [] getPrivateSSHKey() { 

        // Check if we can find the files we need to setup an outgoing ssh 
        // connection.      
        String home = System.getProperty("user.home");
        String sep = System.getProperty("file.separator");
        
        // TODO: add windows/solaris options ?
        String [] fileNames = new String [] { 
                home + sep + ".ssh" + sep + "id_rsa", 
                home + sep + ".ssh" + sep + "id_dsa"
        };
       
        for (String f : fileNames) { 
            File keyfile = new File(f);
        
            if (keyfile.exists() && keyfile.canRead() && keyfile.length() > 0) { 
                // we have found the keyfile ?
                char [] privateKey = readKeyFile(keyfile);
                
                if (privateKey != null) {
                    return privateKey;
                }
            } else {
                if (logger.isInfoEnabled()) {
                    logger.info("SSH private key not found: " + f);   
                }
            }
        }

        if (logger.isInfoEnabled()) {
            logger.info("No SSH private key found, outgoing SSH " +
                    "connections disabled");   
        }
        
        //String keyfilePass = "joespass"; // will be ignored if not needed
        return null;
    }
    
    private char [] readKeyFile(File keyFile) { 
 
        try {
            char [] result = new char[(int) keyFile.length()];

            FileReader fr = new FileReader(keyFile);

            int read = fr.read(result);

            if (read != keyFile.length()) { 
                if (logger.isInfoEnabled()) {
                    logger.info("Failed to read SSH private key in: " + 
                            keyFile.getAbsolutePath());
                }

                return null;
            }
            
            if (logger.isInfoEnabled()) {
                logger.info("Succesfully read SSH private key in: " + 
                        keyFile.getAbsolutePath());
            }

            return result;
            
        } catch (IOException e) {
            
            if (logger.isInfoEnabled()) {
                logger.info("Failed to read SSH private key in: " + 
                        keyFile.getAbsolutePath() + " ", e);
            }
            
            return null;
        }
    }
    
    private byte [] toBytes(IPAddressSet address) { 
        
        byte [] tmp = address.getAddress();
        
        byte [] result = new byte[2+tmp.length];
            
        result[0] = (byte) (tmp.length & 0xFF);
        result[1] = (byte) ((tmp.length >> 8) & 0xFF);
        System.arraycopy(tmp, 0, result, 2, tmp.length);
        
        return result;
    }

    private byte [] toBytes(String s) { 
        
        byte [] tmp = (s == null ? new byte[0] : s.getBytes());
        byte [] result = new byte[2+tmp.length];
            
        result[0] = (byte) (tmp.length & 0xFF);
        result[1] = (byte) ((tmp.length >> 8) & 0xFF);
        System.arraycopy(tmp, 0, result, 2, tmp.length);
        
        return result;
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
            externalAddress = IPAddressSet.getFromAddress(externalNATAddress);
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
        
            if (externalNATAddress != null) {
                externalAddress = IPAddressSet.getFromAddress(externalNATAddress);
                return;
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
            
            if (externalNATAddress != null) {
                externalAddress = IPAddressSet.getFromAddress(externalNATAddress);
                return;
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

    private DirectSocket attemptSSHForwarding(SocketAddressSet sas, 
            InetSocketAddress target, InetSocketAddress forwardTo, 
            Connection conn, long start) throws FirewallException {         
       
        LocalStreamForwarder lsf = null;
               
        String fowardTarget = forwardTo.getAddress().toString();
        
        if (fowardTarget.startsWith("/")) { 
            fowardTarget = fowardTarget.substring(1);
        }
                        
        if (logger.isDebugEnabled()) {               
            logger.debug("Attempting SSH forwarding to " + fowardTarget + " via"
                    + sas.toString());
        }      
        
        try {                
            lsf = conn.createLocalStreamForwarder(fowardTarget, forwardTo.getPort());            
            
            InputStream in = lsf.getInputStream();
            OutputStream out = lsf.getOutputStream();
            
            SocketAddressSet realAddress = handShake(sas, target, in, out); 
            
            if (realAddress == null) {
                
                if (logger.isInfoEnabled()) {               
                    logger.info("Handshake failed during SSH connection setup to "
                            + NetworkUtils.ipToString(target.getAddress()) + ":"
                            + target.getPort() + " after " 
                            + (System.currentTimeMillis()-start) + " ms.");
                }  
                    
                try { 
                    lsf.close();
                } catch (Exception e2) {
                    // close
                }    
                
            } else {      
                
                // We have a connection!
                if (logger.isInfoEnabled()) {               
                    logger.info("SSH connection setup to " + sas.toString() 
                            + " completed in " + (System.currentTimeMillis()-start) 
                            + " ms.");
                }  
                
                SocketAddressSet a = SocketAddressSet.getByAddress(
                        externalAddress, 1, localAddress, 1, null);
                
                return new DirectSSHSocket(a, realAddress, in, out, lsf);
            }
                
        } catch (FirewallException e) {
       
            try { 
                lsf.close();
            } catch (Exception e2) {
                // close
            }                        
            
            // allowed
            throw e;   
        
        } catch (IOException e) {
        
            if (logger.isInfoEnabled()) {               
                logger.info("Forwarding failed during SSH connection setup to "
                        + NetworkUtils.ipToString(target.getAddress()) + ":"
                        + target.getPort() + " after " 
                        + (System.currentTimeMillis()-start) + " ms.");
            } 
            
            try { 
                lsf.close();
            } catch (Exception e2) {
                // close
            }                        
        }      
        
        return null;    
    }
    
    private DirectSocket attemptSSHConnection(SocketAddressSet sas,
            InetSocketAddress target, int timeout, int localPort, 
            boolean mayBlock, String user) {
        
        DirectSocket result = null;
        long start = 0;
        
        if (logger.isInfoEnabled()) {
            
            start = System.currentTimeMillis();
        
            if (logger.isDebugEnabled()) {                 
                logger.debug("Attempting SSH connection to " + sas.toString()
                        + " using network "
                        + NetworkUtils.ipToString(target.getAddress()) + ":"
                        + target.getPort() + " local port = " + localPort
                        + "timeout = " + timeout);
            }
        }
        
        try {
            String host = target.getAddress().toString();
            
            if (host.startsWith("/")) { 
                host = host.substring(1);
            }
            
            Connection conn = new Connection(host);            
            conn.connect(null, timeout, timeout);
            
            boolean isAuthenticated = conn.authenticateWithPublicKey(user, 
                    privateKey, keyFilePass);

            if (isAuthenticated == false) {
                if (logger.isInfoEnabled()) { 
                    logger.info("Authentication of SSH connection to "
                            + NetworkUtils.ipToString(target.getAddress()) + ":"
                            + target.getPort() + " failed after " 
                            + (System.currentTimeMillis()-start) + " ms.");
                }                  
                
                throw new IOException("Authentication failed.");
            }
            
            if (logger.isDebugEnabled()) {               
                logger.debug("Established SSH connection to " + sas.toString() 
                        + " in " + (System.currentTimeMillis()-start) + " ms.");
            }  
        
            if (!sas.isExternalAddresses(target)) {
            
                // We should be able to foward a connection to the same IP!
                result = attemptSSHForwarding(sas, target, target, conn, start);
            } else { 
                // We should forward to a local IP                 
                for (InetSocketAddress t : sas.getLocalAddresses()) { 
                    result = attemptSSHForwarding(sas, target, t, conn, start);
                    
                    if (result != null) { 
                        break;
                    }
                }
                
                if (result == null) { 
                    // local IP didn't work. Try the global ones ?                  
                    for (InetSocketAddress t : sas.getGlobalAddresses()) { 
                        result = attemptSSHForwarding(sas, target, t, conn, start);
                        
                        if (result != null) { 
                            break;
                        }
                    }
                }
                
            }
            
            if (result == null && logger.isInfoEnabled()) {               
                logger.info("Failed to forward to target machine during SSH " +
                        "connection setup to "
                        + NetworkUtils.ipToString(target.getAddress()) + ":"
                        + target.getPort() + " after " 
                        + (System.currentTimeMillis()-start) + " ms.");
            }  

        } catch (IOException e) {
            
            if (logger.isDebugEnabled()) {
                logger.debug("Failed to create SSH connection to "
                        + NetworkUtils.ipToString(target.getAddress()) + ":"
                        + target.getPort() + " after " 
                        + (System.currentTimeMillis()-start) + " ms.", e);
            } else if (logger.isInfoEnabled()) {
                logger.info("Failed to create SSH connection to "
                        + NetworkUtils.ipToString(target.getAddress()) + ":"
                        + target.getPort() + " after " 
                        + (System.currentTimeMillis()-start) + " ms.");
            }
        }

        return result; 
    }
        
    private DirectSocket attemptConnection(SocketAddressSet sas,
            InetSocketAddress target, int timeout, int localPort, 
            boolean mayBlock) throws FirewallException {

        // We never want to block, so ensure that timeout > 0
        if (timeout == 0 && !mayBlock) {
            timeout = DEFAULT_TIMEOUT;
        }

        Socket s = null;
        InputStream in = null;
        OutputStream out = null;
        
        long start = 0;
        
        if (logger.isInfoEnabled()) {
            
            start = System.currentTimeMillis();
        
            if (logger.isDebugEnabled()) {                 
                logger.debug("Attempting connection to " + sas.toString()
                        + " using network "
                        + NetworkUtils.ipToString(target.getAddress()) + ":"
                        + target.getPort() + " local port = " + localPort
                        + " timeout = " + timeout);
            }
        }
        
        try {
            s = createUnboundSocket();

            if (localPort > 0) {
                s.bind(new InetSocketAddress(localPort));
            }
            
            s.connect(target, timeout);

            if (logger.isDebugEnabled()) {               
                logger.debug("Established connection to " + sas.toString() 
                        + " in " + (System.currentTimeMillis()-start) + " ms.");
            }  
            
             // TODO: optimized this ??? Its getting more and more complicated!!
             s.setSoTimeout(5000);
             s.setTcpNoDelay(true);

             // Check if we are talking to the right machine...
             in = s.getInputStream();
             out = s.getOutputStream();
             
             SocketAddressSet realAddress = handShake(sas, target, in, out);
             
             if (realAddress == null) { 
                 
                 if (logger.isInfoEnabled()) {               
                     logger.info("Handshake failed during connection setup to "
                             + NetworkUtils.ipToString(target.getAddress()) + ":"
                             + target.getPort() + " after " 
                             + (System.currentTimeMillis()-start) + " ms.");
                 }  

                 close(s, out, in);     
                 return null;
             }
             
             s.setSoTimeout(0);
             
             // TODO: get real port here ? 
             SocketAddressSet a = SocketAddressSet.getByAddress(
                     externalAddress, 1, localAddress, 1, null);
             
             DirectSocket r = new DirectSimpleSocket(a, realAddress, in, out, s);
             
             tuneSocket(r);
             
             if (logger.isInfoEnabled()) {               
                 logger.info("Connection setup to " + sas.toString() 
                         + " completed in " + (System.currentTimeMillis()-start) 
                         + " ms.");
             }  
             
             return r; 
        
        } catch (FirewallException e) {
            
            // allowed
            close(s, out, in);
            
            throw e;
            
        } catch (IOException e) {
            
            close(s, out, in);
            
            
            if (logger.isDebugEnabled()) {
                logger.debug("Failed to directly connect to "
                        + NetworkUtils.ipToString(target.getAddress()) + ":"
                        + target.getPort() + " after " 
                        + (System.currentTimeMillis()-start) + " ms.", e);
            } else if (logger.isInfoEnabled()) {
                logger.info("Failed to directly connect to "
                        + NetworkUtils.ipToString(target.getAddress()) + ":"
                        + target.getPort() + " after " 
                        + (System.currentTimeMillis()-start) + " ms.");
            }

            return null;
        }
    }
             
    private SocketAddressSet handShake(SocketAddressSet sas, 
            InetSocketAddress target, InputStream in, OutputStream out) 
        throws FirewallException { 
        
        SocketAddressSet server = null;
        int opcode = -1;
        
        try {     
            // Read the size of the machines address
            int size = (in.read() & 0xFF);
            size |= ((in.read() & 0xFF) << 8); 

            // Read the address itself....
            byte [] tmp = new byte[size];

            int off = 0; 

            while (off < size) { 
                off += in.read(tmp, off, size-off);
            }

            // Now send our own address to the server side (who may decide to 
            // -NOT- accept us based on this)
            out.write(completeAddressInBytes);
            out.write(networkNameInBytes);
            out.flush();

            // Create the address and see if we are to talking to the right 
            // process....
            server = SocketAddressSet.getByAddress(tmp);

            if (!server.sameProcess(sas)) { 
                out.write(DirectServerSocket.WRONG_MACHINE);
                out.flush();

                if (logger.isInfoEnabled()) { 
                    logger.info("Got connecting to wrong machine: "  
                            + sas.toString()
                            + " using network "
                            + NetworkUtils.ipToString(target.getAddress()) + ":"
                            + target.getPort() 
                            + " got me a connection to " 
                            + server.toString() 
                            + " will retry!");
                }

                return null;
            }

            out.write(DirectServerSocket.ACCEPT);             
            out.flush();

            opcode = in.read();

        } catch (Exception e) {

            if (logger.isInfoEnabled()) { 
                logger.info("Handshake with target " 
                        + NetworkUtils.ipToString(target.getAddress()) + ":"
                        + target.getPort() + " failed!", e);
            }

            return null;
        }

        if (opcode != DirectServerSocket.ACCEPT) { 
            if (logger.isInfoEnabled()) { 
                logger.info("Target " 
                        + NetworkUtils.ipToString(target.getAddress()) + ":"
                        + target.getPort() + " refused our connection!");
            }

            throw new FirewallException("Connection refused by receivers" 
                    + " firewall!");
        }

        return server;
    }

    private DirectServerSocket createServerSocket(int port, int backlog,
            boolean portForwarding, boolean forwardingMayFail,
            boolean sameExternalPort) throws IOException {

        if (port == 0) {
            port = portRange.getPort();
        }
        
        if (backlog == 0) { 
            backlog = DEFAULT_BACKLOG;
        }

        ServerSocket ss = createUnboundServerSocket();
        ss.bind(new InetSocketAddress(port), backlog);
        
        if (!(haveOnlyLocalAddresses && portForwarding)) {
            // We are not behind a NAT box or the user doesn't want port
            // forwarding, so just return the server socket
            SocketAddressSet a = SocketAddressSet.getByAddress(
                    externalAddress, ss.getLocalPort(), 
                    localAddress, ss.getLocalPort(), user);
                        
            DirectServerSocket smss = new DirectServerSocket(a, ss, preference);
            
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
                SocketAddressSet a = SocketAddressSet.getByAddress(
                        externalAddress, ss.getLocalPort(), localAddress, 
                        ss.getLocalPort(), user);
                               
                DirectServerSocket smss = new DirectServerSocket(a, ss, preference);
                
                if (logger.isDebugEnabled()) {
                    logger.debug("Port forwarding not allowed for: " + smss);
                }

                return smss;
            }

            // The user does not want the serversocket if it's not forwarded, so 
            // close it and throw an exception.
            logger.warn("Failed to create DirectServerSocket: " +
                    "port forwarding not allowed!");
            
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

        SocketAddressSet local = null;
        
        try {
            int ePort = sameExternalPort ? port : 0;
            ePort = UPNP.addPortMapping(port, ePort, myNATAddress, 0, "TCP");
            
            local = SocketAddressSet.getByAddress(externalAddress, 
                    new int [] { ePort }, localAddress, 
                    new int [] { ss.getLocalPort() }, user);
          
        } catch (Exception e) {

            logger.warn("Port forwarding failed! ", e);

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
            
            local = SocketAddressSet.getByAddress(localAddress, 
                    ss.getLocalPort(), user);
        }

        DirectServerSocket smss = new DirectServerSocket(local, ss, preference);
        
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
   
    public static void close(Socket s, OutputStream o, InputStream i) {
        
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
        return createSocket(target, timeout, 0, properties, false);
    }

    private boolean mayUseSSH(SocketAddressSet target, Map properties) { 
        
        if (ALLOW_SSH_OUT && target.getUser() != null) { 

            if (properties != null && properties.containsKey("allowSSH")) {
                String result = (String) properties.get("allowSSH");

                if (result != null && result.equalsIgnoreCase("true")) {
                    System.err.println("Can use SSH for connection setup!");
                    return true;
                } else {
                    System.err.println("Can NOT use SSH for connection setup!");
                    return false;
                }
            } else {
                System.err.println("Can use SSH for connection setup!");
                return true;
            }

        } 

        System.err.println("Can NOT use SSH for connection setup!" 
                + ALLOW_SSH_OUT + " " + target.getUser());
        
        return false;        
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see smartnet.factories.ClientServerSocketFactory#createClientSocket(smartnet.IbisSocketAddress,
     *      int, java.util.Map)
     */
    public DirectSocket createSocket(SocketAddressSet target, int timeout,
            int localPort, Map properties) throws IOException {
        return createSocket(target, timeout, localPort, properties);
    } 
    
    /*
     * (non-Javadoc)
     * 
     * @see smartnet.factories.ClientServerSocketFactory#createClientSocket(smartnet.IbisSocketAddress,
     *      int, java.util.Map)
     */
    public DirectSocket createSocket(SocketAddressSet target, int timeout,
            int localPort, Map properties, boolean fillTimeout) throws IOException {

        if (timeout < 0) {
            timeout = DEFAULT_TIMEOUT;
        }
/*
        AddressIterator itt = preference.determineOrder(target);
     
        // TODO: count options here (after applying the preference!)
        int options = itt.size();
        
        if (options == 1) { 
            long time = System.currentTimeMillis();
            
            // only one option, so allow sleeping for ever...
            DirectSocket result = attemptConnection(target, itt.next(), timeout,
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
        
        int current = 0;
        
        while (true) { 
        
            while (itt.hasNext()) { 

                long time = System.currentTimeMillis();

                InetSocketAddress sa = itt.next();

                int partialTimeout;

                if (sa.getAddress().isLinkLocalAddress() && timeout > 2000) { 
                    partialTimeout = 2000;
                } else { 
                    partialTimeout = timeout / (options-current);
                }

                DirectSocket result = attemptConnection(target, sa, partialTimeout,
                        localPort, false);

                time = System.currentTimeMillis() - time;

                if (result != null) {
                    if (logger.isInfoEnabled()) {      
                        logger.info("Direct connection setup took: "  + time + " ms.");
                    }
                    return result;
                }

                if (logger.isInfoEnabled()) {      
                    logger.info("Direct connection failed: "  + time + " ms.");
                }
            }

            if (timeout > 0) {
                // Enough tries so, throw exception
                throw new SocketTimeoutException("Connection setup timed out!");
            } // else, the user wants us to keep trying!
            
            itt.reset();
        }
  */      
        
        
        /*
         * OLD IMPLEMENTATION BELOW!!!!
         */

        // First check if the number of connect options is doubled by the fact 
        // that we can also use SSH tunnels to connect to the machine...
        boolean mayUseSSH = mayUseSSH(target, properties);
        
        // Next, get the addresses of the target machine.         
        InetSocketAddress[] sas = target.getSocketAddresses();
        
        if (sas.length == 0) { 
            System.err.println("EEK: sas.length == 0!!!");
            return null;
        }
        
        // If there is only one address, and no SSH we can do a blocking call...        
        if (sas.length == 1 && !mayUseSSH) {

            DirectSocket result = attemptConnection(target, sas[0], timeout,
                    localPort, true);
         
            if (result != null) {
                return result;
            }

            throw new ConnectException("Connection setup failed");
        }

        // Select the addresses we want from the target set. Some may be removed
        // thanks to the cluster configuration.
        
        // TODO: shouldn't this be done first ?  
        sas = preference.sort(sas, false);
        
        if (sas.length == 0) { 
            return null;
        }        
        
        // else, we must try them all, so the connection attempt must return at 
        // some point, even if timeout == 0
        
        int timeLeft = timeout;

        if (timeLeft == 0) { 
            timeLeft = DEFAULT_TIMEOUT;
        }
        
        DirectSocket result = null;
        
        do {
            
            int partialTime = timeLeft;
            
            if (mayUseSSH) { 
                partialTime = timeLeft / 2;
            }
            
            long starttime = System.currentTimeMillis();
            
            result = loopOverOptions(target, sas, localPort, partialTime, null);
            
            int time = (int) (System.currentTimeMillis() - starttime);
            
            // If we don't have a connection yet we try to use SSH
            if (result == null && mayUseSSH) { 
                
                partialTime = timeLeft - time;
                
                if (partialTime <= 0) { 
                    if (timeout > 0) { 
                        // Enough tries so, throw exception
                        throw new SocketTimeoutException("Connection setup timed out!");
                    } else { 
                        // TODO: HACK
                        partialTime = DEFAULT_TIMEOUT;
                    }
                } 
            
                result = loopOverOptions(target, sas, localPort, 
                        partialTime, target.getUser());
            
                time = (int) (System.currentTimeMillis() - starttime);
            }
            
            if (result != null) { 
                return result;
            }
            
            timeLeft -= time;
            
            if (timeout == 0) { 
                // the user wants us to keep trying for ever!
                timeLeft = DEFAULT_TIMEOUT;
            } else if (timeLeft <= 0 && timeout > 0) {
                // deadline expired so throw exception
                throw new SocketTimeoutException("Connection setup timed out!");
            } 
        
        } while (fillTimeout);
        
        throw new IOException("Connection setup failed!");
    }

    private DirectSocket loopOverOptions(SocketAddressSet target, 
            InetSocketAddress [] sas, int localPort, int timeout, 
            String user) throws FirewallException {
        
        //System.out.println("loopOverOptions " + timeout);
        
        DirectSocket result = null;
        
        int timeLeft = timeout;
        
        for (int i = 0; i < sas.length; i++) {
            
            long time = System.currentTimeMillis();
            
            InetSocketAddress sa = sas[i];
    
            int partialTime = timeLeft / (sas.length-i);
            
            if (NetworkUtils.isLocalAddress(sa.getAddress()) && partialTime > 1000) {
                // local networks get limited time!
                partialTime = 1000;
            }

            if (user != null) { 
                result = attemptSSHConnection(target, sa, partialTime, 
                        localPort, false, user);                
            } else { 
                result = attemptConnection(target, sa, partialTime, localPort, 
                        false);
            }
            
            timeLeft -= (System.currentTimeMillis() - time);
            
            if (result != null || timeLeft <= 0) {
                break;
            }
        }
        
        if (logger.isInfoEnabled()) {      
            if (result != null) { 
                logger.info((user != null ? "SSH" : "Direct") + " connection "
                        + "setup took: "  + (timeout-timeLeft) + " ms.");
            } else { 
                logger.info((user != null ? "SSH" : "Direct") + " connection " 
                        + "failed: "  + (timeout-timeLeft) + " ms.");
            }
        }
        
        return result;        
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
