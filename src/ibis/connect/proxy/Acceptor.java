package ibis.connect.proxy;

import ibis.connect.direct.DirectServerSocket;
import ibis.connect.direct.DirectSocket;
import ibis.connect.direct.DirectSocketFactory;
import ibis.connect.direct.SocketAddressSet;
import ibis.connect.proxy.connections.ClientConnection;
import ibis.connect.proxy.connections.Connections;
import ibis.connect.proxy.connections.ProxyConnection;
import ibis.connect.proxy.state.ProxyDescription;
import ibis.connect.proxy.state.ProxyList;
import ibis.connect.proxy.state.StateCounter;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Iterator;

public class Acceptor extends CommunicationThread {

    private DirectServerSocket server;
    private boolean done = false;

    private class SpliceInfo { 
        
        String connectID;
        long bestBefore; 
        
        DirectSocket s;
        DataInputStream in;
        DataOutputStream out;

    }
    
    private long nextInvalidation = -1;
   
    private HashMap spliceInfo = new HashMap();
        
    Acceptor(StateCounter state, Connections connections, 
            ProxyList knownProxies, DirectSocketFactory factory) 
            throws IOException {

        super("ProxyAcceptor", state, connections, knownProxies, factory);        

        server = factory.createServerSocket(DEFAULT_PORT, 50, null);        
        setLocal(server.getAddressSet());        
    }

    private boolean handleIncomingProxyConnect(DirectSocket s, 
            DataInputStream in, DataOutputStream out) throws IOException { 

        String otherAsString = in.readUTF();        
        SocketAddressSet addr = new SocketAddressSet(otherAsString); 

        logger.info("Got connection from " + addr);

        ProxyDescription d = knownProxies.add(addr);        
        d.setCanReachMe();

        ProxyConnection c = 
            new ProxyConnection(s, in, out, d, connections, knownProxies, state);

        if (!d.createConnection(c)) { 
            // There already was a connection with this proxy...            
            logger.info("Connection from " + addr + " refused (duplicate)");

            out.write(ProxyProtocol.CONNECTION_REFUSED);
            out.flush();
            return false;
        } else {                         
            // We just created a connection to this proxy.
            logger.info("Connection from " + addr + " accepted");

            out.write(ProxyProtocol.CONNECTION_ACCEPTED);            
            out.flush();

            // Now activate it. 
            c.activate();
            
            connections.addConnection(otherAsString, c);
            return true;
        }     
    }

    private boolean handlePing(DirectSocket s, 
            DataInputStream in, DataOutputStream out) throws IOException {

        String sender = in.readUTF();         
        logger.info("Got ping from: " + sender);      
        return false;
    }    

    private boolean handleServiceLinkConnect(DirectSocket s, DataInputStream in,
            DataOutputStream out) {

        try { 
            String src = in.readUTF();
            
            if (connections.getConnection(src) != null) { 
                if (logger.isDebugEnabled()) { 
                    logger.debug("Incoming connection from " + src + 
                    " refused, since it already exists!"); 
                } 

                out.write(ProxyProtocol.SERVICELINK_REFUSED);
                out.flush();
                DirectSocketFactory.close(s, out, in);
                return false;
            }

            if (logger.isDebugEnabled()) { 
                logger.debug("Incoming connection from " + src + " accepted"); 
            } 

            out.write(ProxyProtocol.SERVICELINK_ACCEPTED);
            out.writeUTF(server.getAddressSet().toString());            
            out.flush();

            ClientConnection c = new ClientConnection(src, s, in, out, 
                    connections, knownProxies);
            connections.addConnection(src, c);                                               
            c.activate();

            knownProxies.getLocalDescription().addClient(src);
            return true;

        } catch (IOException e) { 
            logger.warn("Got exception while handling connect!", e);
            DirectSocketFactory.close(s, out, in);
        }  

        return false;
    }

    private boolean handleBounce(DirectSocket s, DataInputStream in, 
            DataOutputStream out) throws IOException {
        
        out.write(s.getLocalAddress().getAddress());
        out.flush();
        
        return false;
    }
        
    private boolean handleSpliceInfo(DirectSocket s, DataInputStream in, 
            DataOutputStream out) throws IOException {

        boolean result = false;
        
        String connectID = in.readUTF();
        int time = in.readInt();
        
        logger.info("Got request for splice info: " + connectID + " " + time);
        
        SpliceInfo info = (SpliceInfo) spliceInfo.remove(connectID);
        
        if (info == null) {
            
            logger.info("Request " + connectID + " is first");
                        
            // We're the first...            
            info = new SpliceInfo();
            
            info.connectID = connectID;
            info.s = s;
            info.out = out;
            info.in = in;             
            info.bestBefore = System.currentTimeMillis() + time;
            
            spliceInfo.put(connectID, info);
            
            if (nextInvalidation == -1 || info.bestBefore < nextInvalidation) { 
                nextInvalidation = info.bestBefore;
            }            
            
            // Thats it for now. Return true so the connection is kept open 
            // until the other side arrives...
            result = true;        
        } else {
            logger.info("Request " + connectID + " is second");
            
            
            // The peer is already waiting...
            
            // We now echo the essentials of the two connection that we see to 
            // each client. Its up to them to decide what to do with it....
            
            try { 
                InetSocketAddress tmp = 
                    (InetSocketAddress) s.getRemoteSocketAddress();

                info.out.writeUTF(tmp.getAddress().toString());
                info.out.writeInt(tmp.getPort());
                info.out.flush();
                
                tmp = (InetSocketAddress) info.s.getRemoteSocketAddress();

                out.writeUTF(tmp.getAddress().toString());
                out.writeInt(tmp.getPort());
                out.flush();
                
            } catch (Exception e) {                
                // The connections may have been closed already....
                logger.info("Failed to forward splice info!", e);               
            } finally { 
                // We should close the first connection. The second will be 
                // closed for us when we return false
                DirectSocketFactory.close(info.s, info.out, info.in);
            }            
        }
        
        // Before we return, we do some garbage collection on the spliceInfo map
        if (nextInvalidation != -1) {  

            long now = System.currentTimeMillis();
              
            if (now >= nextInvalidation) { 

                nextInvalidation = Long.MAX_VALUE;
                
                // Traverse the map, removing all entries which are out of date, 
                // and recording the next time at which we should do a 
                // traversal.  
                Iterator itt = spliceInfo.keySet().iterator();
                
                while (itt.hasNext()) { 
                    String key = (String) itt.next();                   
                    SpliceInfo tmp = (SpliceInfo) spliceInfo.get(key);
                    
                    if (tmp.bestBefore < now) { 
                        spliceInfo.remove(key);
                    } else { 
                        if (tmp.bestBefore < nextInvalidation) { 
                            nextInvalidation = tmp.bestBefore;
                        } 
                    } 
                }

                if (spliceInfo.size() == 0) { 
                    nextInvalidation = -1;
                }
            } 
        } 

        return result;
    }
    
    private void doAccept() {

        DirectSocket s = null;
        DataInputStream in = null;
        DataOutputStream out = null;
        boolean result = false;

        logger.info("Doing accept.");

        try {
            s = server.accept();                
            in = new DataInputStream(
                    new BufferedInputStream(s.getInputStream()));

            out = new DataOutputStream(
                    new BufferedOutputStream(s.getOutputStream()));

            int opcode = in.read();

            switch (opcode) {
            case ProxyProtocol.CONNECT:
                result = handleIncomingProxyConnect(s, in, out);                   
                break;

            case ProxyProtocol.PING:
                result = handlePing(s, in, out);                   
                break;
              
            case ProxyProtocol.SERVICELINK_CONNECT:
                result = handleServiceLinkConnect(s, in, out);
                break;                

            case ProxyProtocol.BOUNCE_IP:
                result = handleBounce(s, in, out);
                break;                
            
            case ProxyProtocol.GET_SPLICE_INFO:
                result = handleSpliceInfo(s, in, out);
                break;                
                            
            default:
                break;
            }
        } catch (Exception e) {
            logger.warn("Failed to accept connection!", e);
            result = false;
        }

        if (!result) { 
            DirectSocketFactory.close(s, out, in);
        }   
    }

    public void run() { 

        while (!done) {           
            doAccept();            
        }
    }       
}
