package ibis.connect.virtual.modules.splice;

import ibis.connect.direct.DirectSocket;
import ibis.connect.direct.DirectSocketFactory;
import ibis.connect.direct.SocketAddressSet;
import ibis.connect.proxy.ProxyProtocol;
import ibis.connect.virtual.ModuleNotSuitableException;
import ibis.connect.virtual.VirtualServerSocket;
import ibis.connect.virtual.VirtualSocket;
import ibis.connect.virtual.VirtualSocketAddress;
import ibis.connect.virtual.modules.AbstractDirectModule;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.StringTokenizer;

public class Splice extends AbstractDirectModule {
   
    protected static final byte ACCEPT              = 1;
    protected static final byte PORT_NOT_FOUND      = 2;
    protected static final byte WRONG_MACHINE       = 3;     
    protected static final byte CONNECTION_REJECTED = 4;   
    
    private static final int PLEASE_CONNECT = 1;
    
    private static final int MAX_ATTEMPTS = 3;
    private static final int DEFAULT_TIMEOUT = 1000;
    private static final int PORT_RANGE = 5;
        
    private DirectSocketFactory factory;               
    
    private long nextID = 0;
    
    public Splice() {
        super("ConnectModule(Splice)", true);
    }
        
    private synchronized long getID() { 
        return nextID++;
    }
    
    public VirtualSocket connect(VirtualSocketAddress target, int timeout, 
            Map properties) throws ModuleNotSuitableException, IOException {

        // First check if we are trying to connect to ourselves (which makes no 
        // sense for this module... 
        if (target.machine().sameMachine(parent.getLocalHost())) { 
            throw new ModuleNotSuitableException(name + ": Cannot set up " +
                "a connection to myself!"); 
        }
        
        logger.info(name + ": attempting connection setup to " + target);
        
        // Start by extracting the machine address of the target.
        SocketAddressSet targetMachine = target.machine();
        
        // Next we get our own machine address. 
        SocketAddressSet myMachine = parent.getLocalHost();
        
        // Generate a unique ID for this connection setup 
        String connectID = getID() + "@" + myMachine; 
        
        logger.info(name + ": looking for shared proxy between " + myMachine 
                + " and " + targetMachine);
        
        // Now try to find a proxy that both machines can reach.
        SocketAddressSet shared = 
            serviceLink.findSharedProxy(myMachine, targetMachine);
        
        if (shared == null) {            
            logger.info(name + ": no shared proxy found!");
            
            // No shared proxy was found, so we give up!
            throw new ModuleNotSuitableException("Could not find shared " + 
                    "proxy for " + myMachine + " and " + targetMachine);
        }

        logger.info(name + ": shared proxy found " + shared);
                
        // Send a message to the target asking it to participate in the
        // connection attempt. We will not get a reply. 
        serviceLink.send(targetMachine, name, PLEASE_CONNECT, 
                shared + " " + connectID + " " + timeout + " " + target.port());
        
        // Now create the connection to the shared proxy, and get the public
        // IP of the target plus a range of possibly working port....
        SocketAddressSet [] a = new SocketAddressSet[PORT_RANGE];        
        int localPort = getInfo(shared, connectID, timeout, a);

        // Check if proxy returned somethig usefull...
        if (a[0] == null) {             
            logger.info(name + ": failed to contact peer at shared proxy!");            
            throw new ModuleNotSuitableException("Failed to contact peer at " +                    
                    "shared proxy " + shared);
        }
        
        // Try to connect to the target
        DirectSocket s = connect(a, localPort, DEFAULT_TIMEOUT);
                
        if (s == null) { 
            throw new ModuleNotSuitableException(name + ": Failed to connect "
                    + " to " + target);                         
        }

        // If the connection was succesfull, we hand over the socket to the 
        // parent for handshakes, checks, etc.
        return handleConnect(target, s, timeout, properties);
    }
    
    private int getInfo(SocketAddressSet shared, String connectID, int timeout, 
            SocketAddressSet [] result) throws ModuleNotSuitableException { 

        DirectSocket s = null;
        DataInputStream in = null;
        DataOutputStream out = null;

        String addr = null;
        int port = 0;
        int local = 0;
                
        try { 
            s = factory.createSocket(shared, timeout, null);
            s.setReuseAddress(true);            
            
            local = s.getLocalPort();
            
            in = new DataInputStream(s.getInputStream());
            out = new DataOutputStream(s.getOutputStream());

            // TODO: don't like this access here!
            out.writeByte(ProxyProtocol.GET_SPLICE_INFO);
            out.writeUTF(connectID);
            out.writeInt(timeout);
            out.flush();

            addr = in.readUTF();
            port = in.readInt();
            
        } catch (IOException e) {
            
            System.err.println("@@@@  EEK " + e);
            e.printStackTrace(System.err);
            
            // Failed to create the exception, to the shared proxy 
            // TODO: try to find other shared proxy ? 
            throw new ModuleNotSuitableException(name + ": Failed to " +
                    "connect to shared proxy " + shared + " " + e);                   
        } finally { 
            DirectSocketFactory.close(s, out, in);
        }

        try { 
            for (int i=0;i<result.length;i++) {
                result[i] = new SocketAddressSet(addr, port+i);
            }
            
            return local;
        } catch (IOException e) {
            // Failed to create the exception, to the shared proxy 
            // TODO: try to find other shared proxy ? 
            throw new ModuleNotSuitableException(name + ": Failed to " +
                    "parse reply from shared proxy " + shared + " " + e);                   
        } 
    }
    
    private DirectSocket connect(SocketAddressSet [] target, int localPort, 
            int timeout) throws IOException, ModuleNotSuitableException {
        
        // TODO: This will give you very long waiting time before the setup 
        // fails! Better to fail fast and retry the entire setup ? Or maybe 
        // change the timeout to something small ? 
        
        for (int i=0;i<MAX_ATTEMPTS;i++) {
            for (int t=0;t<target.length;t++) {             
                try { 
                    return factory.createSocket(target[t], timeout, localPort, null);
                } catch (IOException e) {
                    logger.info(name + ": Connection failed " 
                            + target.toString(), e);
                }           
            }    
            
            logger.debug(name + ": Splice failed (" + i + ")");
        }
                        
        return null;
    }
    
    public SocketAddressSet getAddresses() {
        // Nothing to do here, since we don't extend the address in any way... 
        return null;
    }

    public void initModule(Map properties) throws Exception {
        // Create a direct socket factory.
        factory = DirectSocketFactory.getSocketFactory();       
    }

    public boolean matchAdditionalRuntimeRequirements(Map requirements) {
        // Alway match ? 
        return true;
    }

    public void startModule() throws Exception {
        
        if (serviceLink == null) {
            throw new Exception(name + ": no service link available!");       
        }
        
        // Create a direct socket factory. 
        factory = DirectSocketFactory.getSocketFactory();          
    }
    
    private void setupConnection(SocketAddressSet shared, String connectID, 
            int timeout) {

        try { 
            // Create the connection to the shared proxy, and get the required
            // information on where to connect to
            SocketAddressSet [] a = new SocketAddressSet[PORT_RANGE];
            int local = getInfo(shared, connectID, timeout, a);
                        
            DirectSocket s = connect(a, local, timeout);
            
            if (s == null) {  
                logger.info(name + ": Incoming connection setup failed!");
                return;
            }
               
            handleAccept(s);
                        
        } catch (Exception e) {
            logger.info(name + ": Incoming connection setup failed!", e);            
        }
    }
    
    public void gotMessage(SocketAddressSet src, int opcode, String message) {

        logger.info(name + ": got message " + src + " " + opcode 
                + " \"" +  message + "\"");
               
        // Check if the opcode makes any sense
        if (opcode != PLEASE_CONNECT) { 
            logger.warn(name + ": ignoring message " + src + " " + opcode 
                    + "\"" +  message + "\"");
            return;
        }
                
        // Next, check if the message has enough parts
        StringTokenizer t = new StringTokenizer(message);
        
        if (t.countTokens() != 4) { 
            logger.warn(name + ": malformed message " + src + " " + opcode 
                    + "\"" +  message + "\"");
            return;
        }
        
        // Try to extract the necessary info from the message 
        SocketAddressSet shared = null;
        String connectID = null;
        int timeout = 0;
        int port = 0;
        
        try { 
            shared = new SocketAddressSet(t.nextToken());
            connectID = t.nextToken();        
            timeout = Integer.parseInt(t.nextToken());
            port = Integer.parseInt(t.nextToken());
        } catch (Exception e) {
            logger.warn(name + ": failed to parse message " + src + " " + opcode 
                    + "\"" +  message + "\"", e);
            return;
        }
        
        // Check if we can find the port that the sender is interrested in
        VirtualServerSocket ss = parent.getServerSocket(port);
        
        if (ss == null) {
            // TODO: send reply ??
            logger.info(name + ": port " + port + " not found!");
            return;            
        }

        setupConnection(shared, connectID, timeout);
    }

    protected VirtualSocket createVirtualSocket(VirtualSocketAddress a, 
            DirectSocket s, DataOutputStream out, DataInputStream in) {     
        return new SplicedVirtualSocket(a, s, out, in, null);
    }
}
