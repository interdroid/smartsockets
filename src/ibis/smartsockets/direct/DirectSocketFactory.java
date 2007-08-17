package ibis.smartsockets.direct;

import ibis.smartsockets.SmartSocketsProperties;
import ibis.smartsockets.util.NetworkUtils;
import ibis.smartsockets.util.STUN;
import ibis.smartsockets.util.TypedProperties;
import ibis.smartsockets.util.UPNP;

import java.io.EOFException;
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
import java.util.LinkedList;
import java.util.Map;

import org.apache.log4j.Logger;

import ch.ethz.ssh2.Connection;
import ch.ethz.ssh2.LocalStreamForwarder;

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

    protected static Logger logger = Logger.getLogger("ibis.smartsockets.direct");
    
    private static DirectSocketFactory defaultFactory; 

    private final int DEFAULT_TIMEOUT;
    private final int DEFAULT_BACKLOG;
    private final int DEFAULT_LOCAL_TIMEOUT;
    
  //  private final TypedProperties properties;
    
    private final boolean USE_NIO;
    
    private final boolean ALLOW_UPNP;
    private final boolean ALLOW_UPNP_PORT_FORWARDING;        
    private final boolean ALLOW_SSH_OUT;    
    private final boolean FORCE_SSH_OUT;    
        
    private final int defaultReceiveBuffer;
    private final int defaultSendBuffer;
        
    // User for SSH tunneling
    private final String user;
    private final char [] privateKey;
    
    private IPAddressSet localAddress;
    private IPAddressSet externalAddress;  
    private IPAddressSet completeAddress;
    
    //private byte [] completeAddressInBytes;
    private byte [] altCompleteAddressInBytes;
    
   // private byte [] networkNameInBytes;
    
    private InetAddress externalNATAddress;

    private boolean haveOnlyLocalAddresses = false;

    private String myNATAddress;

    private PortRange portRange;

    private NetworkPreference preference;

    private Preference publicFirst;
    
    private String keyFilePass = "";

    private boolean haveFirewallRules;

    private DirectSocketFactory(TypedProperties p) {
        
        //properties = p;
    
        DEFAULT_BACKLOG = p.getIntProperty(SmartSocketsProperties.DIRECT_BACKLOG, 100);
        DEFAULT_TIMEOUT = p.getIntProperty(SmartSocketsProperties.TIMEOUT, 5000);
        DEFAULT_LOCAL_TIMEOUT = p.getIntProperty(SmartSocketsProperties.LOCAL_TIMEOUT, 1000);
          
        boolean allowSSHIn = p.booleanProperty(SmartSocketsProperties.SSH_IN, false);

        if (allowSSHIn) { 
            user = System.getProperty("user.name");
        } else { 
            user = null;
        }
        
        boolean allowSSHOut = p.booleanProperty(SmartSocketsProperties.SSH_OUT, false);
        FORCE_SSH_OUT = p.booleanProperty(SmartSocketsProperties.FORCE_SSH_OUT, false);
        
        if (allowSSHOut) {
            privateKey = getPrivateSSHKey();
        } else { 
            privateKey = null;
        }
        
        ALLOW_SSH_OUT = (privateKey != null);
        
        ALLOW_UPNP = p.booleanProperty(SmartSocketsProperties.UPNP, false);

        if (!ALLOW_UPNP) {             
            ALLOW_UPNP_PORT_FORWARDING = false;
        } else { 
            ALLOW_UPNP_PORT_FORWARDING = 
                p.booleanProperty(SmartSocketsProperties.UPNP_PORT_FORWARDING, 
                        false);
        }
        
        USE_NIO = p.booleanProperty(SmartSocketsProperties.DIRECT_NIO, false);
        
        defaultReceiveBuffer = 
            p.getIntProperty(SmartSocketsProperties.DIRECT_SEND_BUFFER, 0);
        
        defaultSendBuffer = 
            p.getIntProperty(SmartSocketsProperties.DIRECT_RECEIVE_BUFFER, 0);
                        
        localAddress = IPAddressSet.getLocalHost();            
                
        if (!localAddress.containsPublicAddress()) {
            haveOnlyLocalAddresses = true;

            byte [] uuid = NetworkUtils.getUUID();
            
            localAddress = IPAddressSet.merge(localAddress, uuid);    
            
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

        publicFirst = new Preference("PublicBeforePrivate", false);
        publicFirst.addGlobal();
        publicFirst.addSite();
        publicFirst.addLink();
        
        if (logger.isInfoEnabled()) {
            logger.info("Local address: " + completeAddress);
            logger.info("Local network: " + 
                 (preference == null ? "<none>" : preference.getNetworkName()));
        }
        
        altCompleteAddressInBytes = toBytes(5, completeAddress, 
                preference == null ? null : preference.getNetworkName());
         
        getNATAddress();
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
    
    protected static byte [] toBytes(int header, IPAddressSet address, int trailer) { 
        
        byte [] tmp = address.getAddress();
        
        byte [] result = new byte[header + 2 + tmp.length + trailer];
            
        result[header] = (byte) (tmp.length & 0xFF);
        result[header+1] = (byte) ((tmp.length >> 8) & 0xFF);
        System.arraycopy(tmp, 0, result, header+2, tmp.length);
        
        return result;
    }

    protected static byte [] toBytes(int header, IPAddressSet address, 
            String s) { 
        
        byte [] tmp1 = address.getAddress();
        byte [] tmp2 = (s == null ? new byte[0] : s.getBytes());
        
        byte [] result = new byte[header + 4 + tmp1.length + tmp2.length];
        
        result[header] = (byte) (tmp1.length & 0xFF);
        result[header+1] = (byte) ((tmp1.length >> 8) & 0xFF);
       
        System.arraycopy(tmp1, 0, result, header+2, tmp1.length);
        
        int off = header + 2 + tmp1.length;
        
        result[off] = (byte) (tmp2.length & 0xFF);
        result[off+1] = (byte) ((tmp1.length >> 8) & 0xFF);
       
        System.arraycopy(tmp2, 0, result, off+2, tmp2.length);
       
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
     * This method tries to find a public address that is valid for this
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
            logger.debug("SmartSocketsProperties lookup result: " + externalNATAddress);
        }

        if (externalNATAddress != null) {
            externalAddress = IPAddressSet.getFromAddress(externalNATAddress);
            return;
        }

        if (p.booleanProperty(SmartSocketsProperties.STUN, false)) {
            
            if (logger.isDebugEnabled()) {
                logger.debug("Using STUN to find external address...");
            }
   
            String [] servers = p.getStringList(SmartSocketsProperties.STUN_SERVERS, ",", null); 
            
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
            // Try to obtain the public IP address by using UPNP.
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

        String tmp = p.getProperty(SmartSocketsProperties.EXTERNAL_MANUAL);

        if (tmp != null) {
            try {
                result = InetAddress.getByName(tmp);
            } catch (UnknownHostException e) {
                logger.warn("Failed to parse property \""
                        + SmartSocketsProperties.EXTERNAL_MANUAL + "\"");
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

    private DirectSocket attemptSSHForwarding(DirectSocketAddress sas, 
            InetSocketAddress target, InetSocketAddress forwardTo, 
            Connection conn, long start,  byte [] userOut, byte [] userIn, 
            boolean check) throws FirewallException {         
       
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
            
            DirectSocketAddress realAddress = handShake(sas, target, in, out, 
                    userOut, userIn, check); 
            
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
                
                // TODO: is this correct ? 
                DirectSocketAddress a = DirectSocketAddress.getByAddress(
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
    
    private DirectSocket attemptSSHConnection(DirectSocketAddress sas,
            InetSocketAddress target, int timeout, int localPort, 
            boolean mayBlock, String user, byte [] userOut, byte [] userIn, 
            boolean check) throws IOException {
        
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
        
        //try {
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
                
                throw new IOException("SSH authentication failed.");
            }
            
            if (logger.isDebugEnabled()) {               
                logger.debug("Established SSH connection to " + sas.toString() 
                        + " in " + (System.currentTimeMillis()-start) + " ms.");
            }  
        
            if (!sas.inExternalAddress(target)) {
            
                // We should be able to foward a connection to the same IP!
                result = attemptSSHForwarding(sas, target, target, conn, start, 
                        userOut, userIn, true);
            } else { 
                // We should forward to a local IP                 
                for (InetSocketAddress t : sas.getPrivateAddresses()) { 
                    result = attemptSSHForwarding(sas, target, t, conn, start, 
                            userOut, userIn, true);
                    
                    if (result != null) { 
                        break;
                    }
                }
                
                if (result == null) { 
                    // local IP didn't work. Try the public ones ?                  
                    for (InetSocketAddress t : sas.getPublicAddresses()) { 
                        result = attemptSSHForwarding(sas, target, t, conn, 
                                start, userOut, userIn, true);
                        
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
            
                throw new ConnectException("SSH forwarding failed.");
            }  

            /*
        } catch (IOException e) {
            
            if (logger.isInfoEnabled()) {
                logger.info("Failed to create SSH connection to "
                        + NetworkUtils.ipToString(target.getAddress()) + ":"
                        + target.getPort() + " after " 
                        + (System.currentTimeMillis()-start) + " ms.", e);
            }
        }*/

        return result; 
    }
        
    private DirectSocket attemptConnection(DirectSocketAddress sas,
            InetSocketAddress target, int timeout, int sndbuf, int rcvbuf, 
            int localPort, boolean mayBlock, byte [] userOut, byte [] userIn, 
            boolean check) throws IOException {

        // We never want to block, so ensure that timeout > 0
        if (timeout == 0 && !mayBlock) {
            timeout = DEFAULT_TIMEOUT;
        }

        Socket s = null;
        InputStream in = null;
        OutputStream out = null;
        
        long start = 0;
        
        // TODO: may move this into the 'if' below once the 'warn' in the catch 
        // of the IOException has move to a info/debug. 
        start = System.currentTimeMillis();
        
        if (logger.isInfoEnabled()) {
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

            // Must be done here to have any effect!
            tuneSocket(s, sndbuf, rcvbuf);
            
            s.connect(target, timeout);

            if (logger.isDebugEnabled()) {               
                logger.debug("Established connection to " + sas.toString() 
                        + " in " + (System.currentTimeMillis()-start) + " ms.");
            }  
            
            s.setSoTimeout(5000);
            
            // Check if we are talking to the right machine...
            in = s.getInputStream();
            out = s.getOutputStream();

            DirectSocketAddress realAddress = handShake(sas, target, in, out, 
                    userOut, userIn, check);

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

            // TODO: get real port here ? How about the UUID ? 
            DirectSocketAddress a = DirectSocketAddress.getByAddress(
                    externalAddress, 1, localAddress, 1, null);

            DirectSocket r = new DirectSimpleSocket(a, realAddress, in, out, s);

//            tuneSocket(r);

            if (logger.isInfoEnabled()) {               
                logger.info("Connection setup to " + sas.toString() 
                        + " using address " 
                        + NetworkUtils.ipToString(target.getAddress()) 
                        + " completed in " 
                        + (System.currentTimeMillis()-start) 
                        + " ms.");
            }  

            return r; 
        
        } catch (FirewallException e) {
     
            if (logger.isDebugEnabled()) { 
                logger.debug("Failed to connect to "
                        + NetworkUtils.ipToString(target.getAddress()) + ":"
                        + target.getPort() + " after " 
                        + (System.currentTimeMillis()-start) + " ms. (" 
                        + timeout + ") due to simulated firewall. ", e);
            }
            
            // allowed
            close(s, out, in);
            
            throw e;
            
        } catch (IOException e) {
            
            /*
            logger.warn("Failed to connect to "
                    + NetworkUtils.ipToString(target.getAddress()) + ":"
                    + target.getPort() + " after " 
                    + (System.currentTimeMillis()-start) + " ms. (" 
                    + timeout + ") ", e);
             */

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

            throw e;
        }
    }
    
    static int readByte(InputStream in) throws IOException { 
        
        int value = in.read();
        
        if (value == -1) { 
            throw new EOFException("Unexpected EOF");
        }
        
        return value;
    }
    
    static byte [] readFully(InputStream in, byte [] out) throws IOException { 
        int off = 0; 
        
        while (off < out.length) {
            
            int tmp = in.read(out, off, out.length-off);
        
            if (tmp == -1) { 
                throw new EOFException("Unexpected EOF");
            }
           
            off += tmp;
        }
        
        return out;
    }
    
    
    private DirectSocketAddress handShake(DirectSocketAddress sas, 
            InetSocketAddress target, InputStream in, 
            OutputStream out, byte [] userOut, byte [] userIn, 
            boolean checkIdentity) throws FirewallException { 

        // HPDC+Mathijs Version
        DirectSocketAddress server = null;
        int opcode = -1;
        
        try {     
     
            // Start by sending our socket type and address. NOTE this is 
            // dangerous, since it may deadlock if the buffersize is smaller
            // than (1+completeAddressInBytes.length). 
            // TODO: Potential deadlock ? Should fix this ?
            
            synchronized (altCompleteAddressInBytes) {
                
                if (checkIdentity) { 
                    altCompleteAddressInBytes[0] = 
                        DirectServerSocket.TYPE_CLIENT_CHECK;
                } else {
                    altCompleteAddressInBytes[0] = 
                        DirectServerSocket.TYPE_CLIENT_NOCHECK;
                }
                
                for (int i=0;i<4;i++) { 
                    altCompleteAddressInBytes[1+i] = userOut[i];
                }
            
                out.write(altCompleteAddressInBytes);
                out.flush();
            }
            
            // Read the other sides type...
            int type = readByte(in);
            
            // Read the other sides user data 
            readFully(in, userIn);
            
            // Read the size of the machines address
            int size = (readByte(in) & 0xFF);
            size |= ((readByte(in) & 0xFF) << 8); 

            // Read the address itself....
            byte [] tmp = readFully(in, new byte[size]);
            
            // Read the size of the network name
            size = (readByte(in) & 0xFF);
            size |= ((readByte(in) & 0xFF) << 8); 

            // Read the name itself....
            byte [] name = readFully(in, new byte[size]);
            
           // System.out.println("Read address: " + Arrays.toString(tmp));
            
            // Create the address and see if we are to talking to the right 
            // process.
            IPAddressSet ipas = IPAddressSet.getByAddress(tmp);
            
           // System.out.println("Got address: " + ipas);
            
            server = DirectSocketAddress.getByAddress(ipas, 1, null); 
            
            if (checkIdentity) { 
                
                if (!server.sameMachine(sas)) {                     
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
                    
                } else if (haveFirewallRules && 
                        type == DirectServerSocket.TYPE_CLIENT_CHECK) { 

                    // If we are splicing, the firewall rules should be 
                    // checked on the client side!                     
                    String network = new String(name);
                        
                    if (!preference.accept(sas.getSocketAddresses(), network)) { 
                        out.write(DirectServerSocket.FIREWALL_REFUSED);
                        out.flush();

                        if (logger.isInfoEnabled()) { 
                            logger.info("Local firewall refused connection to machine: "  
                                    + sas.toString()
                                    + " using network "
                                    + NetworkUtils.ipToString(target.getAddress()) + ":"
                                    + target.getPort());
                        }
                        
                        throw new FirewallException("Local firewall refused" +
                                " connection to machine: ");
                    }
                }
                 
                out.write(DirectServerSocket.ACCEPT);
                out.flush();                
            } 
            
            if (type == DirectServerSocket.TYPE_SERVER || 
                    type == DirectServerSocket.TYPE_CLIENT_NOCHECK) { 
                // If the other side is an 'open' server, we are done.
                return server; 
                
            } else if (type == DirectServerSocket.TYPE_SERVER_WITH_FIREWALL ||
                    type == DirectServerSocket.TYPE_CLIENT_CHECK) { 
                // If the other side is a server with deny rules, or also a 
                // client, we need to read if other accepts the connection.
                opcode = readByte(in);
                
                if (opcode == DirectServerSocket.FIREWALL_REFUSED) { 
                    if (logger.isInfoEnabled()) { 
                        logger.info("Remote firewall refused connection to machine: "  
                                + sas.toString()
                                + " using network "
                                + NetworkUtils.ipToString(target.getAddress()) + ":"
                                + target.getPort());
                    }
                    
                    throw new FirewallException("Remote firewall refused" +
                            " connection to machine: ");
                
                } else if (opcode != DirectServerSocket.ACCEPT) { 
                    if (logger.isInfoEnabled()) { 
                        logger.info("Connected to the wrong splice" +
                                "attempt of the right machine! " 
                                + NetworkUtils.ipToString(target.getAddress()) + ":"
                                + target.getPort() + " refused our address!");
                    }
                    return null;
                } 
            } else {
                // illegal type!
                logger.warn("Got illegal connection type when connection to:"
                            + NetworkUtils.ipToString(target.getAddress()) + ":"
                            + target.getPort() + " refused our address!");
            
                return null;
            }
        } catch (Exception e) {

            if (logger.isInfoEnabled()) { 
                logger.info("Handshake with target " 
                        + NetworkUtils.ipToString(target.getAddress()) + ":"
                        + target.getPort() + " failed!", e);
            }

            return null;
        }
     
        return server;
    }

    private DirectServerSocket createServerSocket(int port, int receiveBuffer, 
            int backlog, boolean portForwarding, boolean forwardingMayFail,
            boolean sameExternalPort) throws IOException {

        if (port == 0) {
            port = portRange.getPort();
        }
        
        if (backlog < 1) { 
            backlog = DEFAULT_BACKLOG;
        }

        ServerSocket ss = createUnboundServerSocket();
        
        // Must be set here to have any effect...
        if (receiveBuffer > 0) { 
            ss.setReceiveBufferSize(receiveBuffer);
        }
       
        ss.bind(new InetSocketAddress(port), backlog);
        
        if (!(haveOnlyLocalAddresses && portForwarding)) {
            // We are not behind a NAT box or the user doesn't want port
            // forwarding, so just return the server socket
            DirectSocketAddress a = DirectSocketAddress.getByAddress(
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
                DirectSocketAddress a = DirectSocketAddress.getByAddress(
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

        DirectSocketAddress local = null;
        
        try {
            int ePort = sameExternalPort ? port : 0;
            ePort = UPNP.addPortMapping(port, ePort, myNATAddress, 0, "TCP");
            
            local = DirectSocketAddress.getByAddress(externalAddress, 
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
            
            local = DirectSocketAddress.getByAddress(localAddress, 
                    ss.getLocalPort(), user);
        }

        DirectServerSocket smss = new DirectServerSocket(local, ss, preference);
        
        if (logger.isDebugEnabled()) {
            logger.debug("Created server socket on: " + smss);
        }

        return smss;
    }
 
    protected void tuneSocket(Socket s, int send, int receive) throws IOException {
        
        if (send <= 0) { 
            send = defaultSendBuffer;
        }
        
        if (receive <= 0) { 
            receive = defaultReceiveBuffer;
        }
                
        if (send > 0) {
            s.setReceiveBufferSize(send);
        }
        
        if (receive > 0) {
            s.setSendBufferSize(receive);
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
    public DirectSocket createSocket(DirectSocketAddress target, int timeout,
            Map<String, Object> properties) throws IOException {
        return createSocket(target, timeout, 0, -1, -1, properties, false, 0);
    }

    public DirectSocket createSocket(DirectSocketAddress target, int timeout,
            Map<String, Object> properties, int userdata) throws IOException {
        
        return createSocket(target, timeout, 0, -1, -1, properties, false, 
                userdata);
    }

    
    private boolean mayUseSSH(DirectSocketAddress target, 
            Map<String, Object> properties) { 
        
        if (FORCE_SSH_OUT && target.getUser() != null) { 
            return true;
        }
        
        if (ALLOW_SSH_OUT && target.getUser() != null) { 

            if (properties != null && properties.containsKey("allowSSH")) {
                String result = (String) properties.get("allowSSH");

                if (result != null && result.equalsIgnoreCase("true")) {
                    return true;
                } else {
                    return false;
                }
            } else {
                return true;
            }

        } 

       // System.err.println("Can NOT use SSH for connection setup!" 
        //        + ALLOW_SSH_OUT + " " + target.getUser());
        
        return false;        
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see smartnet.factories.ClientServerSocketFactory#createClientSocket(smartnet.IbisSocketAddress,
     *      int, java.util.Map)
     */
    public DirectSocket createSocket(DirectSocketAddress target, int timeout,
            int localPort, Map<String, Object> properties) throws IOException {
        
        return createSocket(target, timeout, localPort, -1, -1, properties, 
                false, 0);
    } 
    
   // public DirectSocket createSocket(int localPort, Map properties) { 
        // TODO: implement!
   //     return null;
   // }
    
    public int getAvailablePort() throws IOException {
        
        Socket s = new Socket();
        
        try { 
            s.bind(null);        
            return s.getLocalPort();
        } finally { 
            s.close();
        } 
    }
    
    private DirectSocket createSingleSocket(DirectSocketAddress target, 
            InetSocketAddress sa, int timeout, int localPort, int sendBuffer, 
            int receiveBuffer, byte [] userOut, byte [] userIn, 
            boolean fillTimeout) throws IOException {

        DirectSocket result = null;
        
        long deadline = 0; 
        int timeleft = timeout; 
        
        if (timeout > 0) {
            deadline = System.currentTimeMillis() + timeout;
        }
        
        do {                
            result = attemptConnection(target, sa, timeleft, sendBuffer, 
                    receiveBuffer, localPort, true, userOut, userIn, false);
        
            if (result != null) {
                int ud = (((userIn[0] & 0xff) << 24) | 
                        ((userIn[1] & 0xff) << 16) |
                        ((userIn[2] & 0xff) << 8) | 
                        (userIn[3] & 0xff));
                
                result.setUserData(ud);
                return result;
            
            } 
                
            timeleft = (int) (deadline - System.currentTimeMillis());
                
            if (timeleft <= 0) { 
                throw new SocketTimeoutException("Timeout during " +
                        "connection setup (" + timeout + ", " 
                        + timeleft + ")");
            }                    
            
        } while (result == null && fillTimeout);
        
        throw new ConnectException("Connection setup failed");
    }

    
    /*
     * (non-Javadoc)
     * 
     * @see smartnet.factories.ClientServerSocketFactory#createClientSocket(smartnet.IbisSocketAddress,
     *      int, java.util.Map)
     */
    public DirectSocket createSocket(DirectSocketAddress target, int timeout,
            int localPort, int sendBuffer, int receiveBuffer, 
            Map<String, Object> properties, boolean fillTimeout, int userData) 
        throws IOException {

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
        
        // Note: it's up to the user to ensure that this thing is large enough!
        // i.e., it should be of size 1+2*target.length
     //   long [] timing = null;
        boolean forceGlobalFirst = false;
        
        if (properties != null) { 
        /*    
            if (!properties.containsKey("direct.detailed.timing.ignore")) { 
                
                timing = (long []) properties.get("direct.detailed.timing");
            
                if (timing != null) { 
                    timing[0] = System.nanoTime();
                }
            }
          */  
            forceGlobalFirst = properties.containsKey("direct.forcePublic");
        }
        
        
       // try { 
        // First check if the number of connect options is doubled by the fact 
        // that we can also use SSH tunnels to connect to the machine...
        boolean mayUseSSH = mayUseSSH(target, properties);

        if (logger.isDebugEnabled()) { 
            logger.debug("Can use SSH for connection setup: " + mayUseSSH);
        }

        // Next, get the addresses of the target machine.         
        InetSocketAddress[] sas = target.getSocketAddresses();

        if (sas.length == 0) { 
            //          System.err.println("EEK: sas.length == 0!!!");
            return null;
        }

        byte [] userIn = new byte[4];
        byte [] userOut = new byte[4];

        userOut[0] = (byte)(0xff & (userData >> 24));
        userOut[1] = (byte)(0xff & (userData >> 16));
        userOut[2] = (byte)(0xff & (userData >> 8));
        userOut[3] = (byte)(0xff & userData);

        // If there is only one address, and no SSH we can do a blocking call...        
        if (sas.length == 1 && !mayUseSSH && !FORCE_SSH_OUT) {

            // if (timing != null) { 
            //     timing[1] = System.nanoTime();
            // }

            // try { 
            return createSingleSocket(target, sas[0], timeout, localPort, 
                    sendBuffer, receiveBuffer, userOut, userIn, 
                    fillTimeout);
            //  } finally { 
            //     if (timing != null) { 
            //        timing[1] = System.nanoTime() - timing[1];
            //    }
            // }
        }

        // Select the addresses we want from the target set. Some may be removed
        // thanks to the cluster configuration.

        // TODO: shouldn't this be done first ?  
        if (forceGlobalFirst) { 
            sas = publicFirst.sort(sas, false);
        } else { 
            sas = preference.sort(sas, false);
        }

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

        LinkedList<NestedIOExceptionData> exceptions = 
            new LinkedList<NestedIOExceptionData>();

        do {

            int partialTime = timeLeft;

            if (mayUseSSH && !FORCE_SSH_OUT) { 
                partialTime = timeLeft / 2;
            }

            long starttime = System.currentTimeMillis();

            if (!FORCE_SSH_OUT) { 
                result = loopOverOptions(target, sas, localPort, partialTime, 
                        sendBuffer, receiveBuffer, null, userOut, userIn, 
                        /*timing*/ null, exceptions);
            }

            int time = (int) (System.currentTimeMillis() - starttime);

            // If we don't have a connection yet we try to use SSH
            if (result == null && mayUseSSH) { 

                partialTime = timeLeft - time;

                if (partialTime <= 0) { 
                    if (timeout > 0) { 
                        // Enough tries so, throw exception
                        throw new NestedIOException("Connection setup timed out!", 
                                exceptions); 
                    } else { 
                        // TODO: HACK
                        partialTime = DEFAULT_TIMEOUT;
                    }
                }
               
                result = loopOverOptions(target, sas, localPort, partialTime, 
                        sendBuffer, receiveBuffer, target.getUser(), userOut, 
                        userIn, /*timing*/ null, exceptions);

                time = (int) (System.currentTimeMillis() - starttime);
            }

            if (result != null) { 

                int ud = (((userIn[0] & 0xff) << 24) | 
                        ((userIn[1] & 0xff) << 16) |
                        ((userIn[2] & 0xff) << 8) | 
                        (userIn[3] & 0xff));

                result.setUserData(ud);
                return result;
            }

            timeLeft -= time;

            if (timeout == 0) { 
                // the user wants us to keep trying for ever!
                timeLeft = DEFAULT_TIMEOUT;
            } else if (timeLeft <= 0 && timeout > 0) {
                // deadline expired so throw exception
                throw new NestedIOException("Connection setup timed out!", 
                        exceptions);
            } 
            
        } while (fillTimeout);

        throw new NestedIOException("Connection setup failed (single attempt)!", 
                exceptions);


        //} finally { 
        //    if (timing != null) { 
        //        timing[0] = System.nanoTime() - timing[0];
        //    }
        //}

    }

    private DirectSocket loopOverOptions(DirectSocketAddress target, 
            InetSocketAddress [] sas, int localPort, int timeout, int sendBuffer, 
            int receiveBuffer, String user, byte [] userOut, byte [] userIn, 
            long [] timing, LinkedList<NestedIOExceptionData> exceptions) 
        throws FirewallException {
        
        //System.out.println("loopOverOptions " + timeout);
        
        DirectSocket result = null;
        
        int timeLeft = timeout;
        
        for (int i = 0; i < sas.length; i++) {
        
            /*
            if (timing != null) { 
                
                if (user == null) { 
                    timing[1+i] = System.nanoTime();
                } else { 
                    timing[1+sas.length+i] = System.nanoTime();
                }
            }
            */
            
            long time = System.currentTimeMillis();
            
            InetSocketAddress sa = sas[i];
            boolean local = false;
            
            int partialTime = timeLeft / (sas.length-i);
            
            if (NetworkUtils.isLocalAddress(sa.getAddress()) 
                    && partialTime > DEFAULT_LOCAL_TIMEOUT) {
                // local networks get limited time!
                partialTime = DEFAULT_LOCAL_TIMEOUT;
                local = true;
            }

            try { 
                if (user != null) { 
                    result = attemptSSHConnection(target, sa, partialTime, 
                            localPort, false, user, userOut, userIn, local);                
                } else { 
                    result = attemptConnection(target, sa, partialTime, sendBuffer, 
                            receiveBuffer, localPort, false, userOut, userIn, local);
                }                
            } catch (IOException e) {
                exceptions.add(new NestedIOExceptionData("Connection setup to " 
                        + NetworkUtils.saToString(sa) + " failed after " 
                        + (System.currentTimeMillis() - time) + " ms." , e));
            }
                
            timeLeft -= (System.currentTimeMillis() - time);
            
            /*
            if (timing != null) { 
                
                if (user == null) { 
                    timing[1+i] = System.nanoTime() - timing[1+i];
                } else { 
                    timing[1+sas.length+i] = System.nanoTime() - timing[1+sas.length+i];
                }
            }*/
            
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
    
    public DirectServerSocket createServerSocket(int port, int backlog, 
            Map prop) throws IOException {
        return createServerSocket(port, backlog, -1, prop);
    }
    
    public DirectServerSocket createServerSocket(int port, int backlog, 
            int receiveBuffer, Map prop) throws IOException {

        boolean forwardMayFail = true;
        boolean sameExternalPort = true;
        boolean portForwarding = getProperty(prop, "PortForwarding", false);

        if (portForwarding) {
            forwardMayFail = getProperty(prop, "ForwardingMayFail", true);
            sameExternalPort = getProperty(prop, "SameExternalPort", true);
        }
        
        return createServerSocket(port, receiveBuffer, backlog, portForwarding,
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
        
        // TODO: cache based on propertties ?         
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
                new DirectSocketFactory(SmartSocketsProperties.getDefaultProperties());                         
        }

        return defaultFactory;
    }
    
    
}
