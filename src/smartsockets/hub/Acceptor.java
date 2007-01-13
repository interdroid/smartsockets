package smartsockets.hub;


import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import org.apache.log4j.Logger;

import smartsockets.direct.DirectServerSocket;
import smartsockets.direct.DirectSimpleSocket;
import smartsockets.direct.DirectSocket;
import smartsockets.direct.DirectSocketFactory;
import smartsockets.direct.SocketAddressSet;
import smartsockets.hub.connections.BaseConnection;
import smartsockets.hub.connections.ClientConnection;
import smartsockets.hub.connections.HubConnection;
import smartsockets.hub.connections.VirtualConnections;
import smartsockets.hub.state.HubDescription;
import smartsockets.hub.state.HubList;
import smartsockets.hub.state.StateCounter;

public class Acceptor extends CommunicationThread {
    
    private static final Logger hconlogger = 
        ibis.util.GetLogger.getLogger("smartsockets.hub.connections.hub"); 
    
    private static final Logger cconlogger = 
        ibis.util.GetLogger.getLogger("smartsockets.hub.connections.client"); 
    
    private static final Logger reglogger = 
        ibis.util.GetLogger.getLogger("smartsockets.hub.registration"); 
    
    private static final Logger reqlogger = 
        ibis.util.GetLogger.getLogger("smartsockets.hub.request"); 
        
    private DirectServerSocket server;
    private boolean done = false;

    private class SpliceInfo { 
        
        String connectID;
        long bestBefore; 
        
        DirectSimpleSocket s;
        DataInputStream in;
        DataOutputStream out;

    }
    
    private long nextInvalidation = -1;
   
    private HashMap<String, SpliceInfo> spliceInfo = new HashMap<String, SpliceInfo>();
        
    Acceptor(int port, StateCounter state, 
            Map<SocketAddressSet, BaseConnection> connections, 
            HubList knownProxies, VirtualConnections vcs,
            DirectSocketFactory factory) throws IOException {

        super("HubAcceptor", state, connections, knownProxies, vcs, factory);        

        server = factory.createServerSocket(port, 50, null);        
        setLocal(server.getAddressSet());        
    }

    private boolean handleIncomingHubConnect(DirectSocket s, 
            DataInputStream in, DataOutputStream out) throws IOException { 

        String otherAsString = in.readUTF();        
        SocketAddressSet addr = SocketAddressSet.getByAddress(otherAsString); 

        if (hconlogger.isDebugEnabled()) { 
            hconlogger.debug("Got connection from " + addr);
        }

        HubDescription d = knownHubs.add(addr);        
        d.setCanReachMe();

        HubConnection c = new HubConnection(s, in, out, d, connections, 
                knownHubs, state, virtualConnections, false);

        if (!d.createConnection(c)) { 
            // There already was a connection with this hub...  
            if (hconlogger.isInfoEnabled()) {
                hconlogger.info("Connection from " + addr + " refused (duplicate)");
            }

            out.write(HubProtocol.CONNECTION_REFUSED);
            out.flush();
            return false;
        } else {                                     
            // We just created a connection to this hub.
          //  if (hconlogger.isInfoEnabled()) {
                hconlogger.warn("Connection accepted from hub " + addr);
           // }

            out.write(HubProtocol.CONNECTION_ACCEPTED);            
            out.flush();

            // Now activate it. 
            c.activate();
            
            connections.put(addr, c);
            return true;
        }     
    }

    private boolean handlePing(DirectSocket s, 
            DataInputStream in, DataOutputStream out) throws IOException {

        String sender = in.readUTF();         
        //logger.info("Got ping from: " + sender);      
        return false;
    }    

    private boolean handleServiceLinkConnect(DirectSocket s, DataInputStream in,
            DataOutputStream out) {

        try { 
            String src = in.readUTF();
            
            SocketAddressSet srcAddr = SocketAddressSet.getByAddress(src);
                        
            if (connections.get(srcAddr) != null) { 
                if (cconlogger.isDebugEnabled()) { 
                    cconlogger.debug("Incoming connection from " + src + 
                    " refused, since it already exists!"); 
                } 

                out.write(HubProtocol.SERVICELINK_REFUSED);
                out.flush();
                DirectSocketFactory.close(s, out, in);
                return false;
            }

         //   if (cconlogger.isDebugEnabled()) { 
            //System.out.println("Incoming connection from " + src 
             //       + " accepted (" + connections.size() + ")");
            
                cconlogger.warn("Incoming connection from " + src 
                        + " accepted (" + connections.size() + ")"); 
         //   } 

            out.write(HubProtocol.SERVICELINK_ACCEPTED);
            out.writeUTF(server.getAddressSet().toString());            
            out.flush();

            ClientConnection c = new ClientConnection(srcAddr, s, in, out, 
                    connections, knownHubs, virtualConnections);
            
            connections.put(srcAddr, c);                                               
            c.activate();

            knownHubs.getLocalDescription().addClient(srcAddr);
            
            if (reglogger.isInfoEnabled()) {
                reglogger.info("Added client: " + src);
            }
            
            return true;

        } catch (IOException e) { 
            cconlogger.warn("Got exception while handling connect!", e);
            DirectSocketFactory.close(s, out, in);
        }  

        return false;
    }

    /*
    private boolean handleBounce(DirectSocket s, DataInputStream in, 
            DataOutputStream out) throws IOException {
        
        out.write(s.getLocalAddress().getAddress());
        out.flush();
        
        return false;
    }*/
        
    private boolean handleSpliceInfo(DirectSocket s, DataInputStream in, 
            DataOutputStream out) throws IOException {

        
        boolean result = false;
        String connectID = in.readUTF();
        int time = in.readInt();
        
        if (reqlogger.isInfoEnabled()) { 
            reqlogger.info("Got request for splice info: " + connectID + " " + time);
        }

        DirectSimpleSocket dss = null;
        
        try { 
            dss = (DirectSimpleSocket) s;
        } catch (ClassCastException e) {
            // Apperently this is not a direct connection, so we give up!

            if (reqlogger.isInfoEnabled()) { 
                reqlogger.info("Cannot handle request for splice info: " 
                        + connectID + ": connection is not direct!");
            }

            DirectSocketFactory.close(s, out, in);
            return false;
        }
        
        SpliceInfo info = null;
        
        synchronized (spliceInfo) {
            info = (SpliceInfo) spliceInfo.remove(connectID);
        }
        
        if (info == null) {
        
            if (reqlogger.isInfoEnabled()) {                
                reqlogger.info("Request " + connectID + " is first");
            }
                        
            // We're the first...            
            info = new SpliceInfo();
            
            info.connectID = connectID;
            info.s = dss;
            info.out = out;
            info.in = in;             
            info.bestBefore = System.currentTimeMillis() + time;
            
            synchronized (spliceInfo) {
                spliceInfo.put(connectID, info);
            }
            
            if (nextInvalidation == -1 || info.bestBefore < nextInvalidation) { 
                nextInvalidation = info.bestBefore;
            }            
            
            // Thats it for now. Return true so the connection is kept open 
            // until the other side arrives...
            result = true;        
        } else {
            if (reqlogger.isInfoEnabled()) {                
                reqlogger.info("Request " + connectID + " is second");
            }
            
            // The peer is already waiting...
            
            // We now echo the essentials of the two connection that we see to 
            // each client. Its up to them to decide what to do with it....
            
            try {                               
                InetSocketAddress tmp = 
                    (InetSocketAddress) dss.getRemoteSocketAddress();

                info.out.writeUTF(tmp.getAddress().toString());
                info.out.writeInt(tmp.getPort());
                info.out.flush();
            
                if (reqlogger.isInfoEnabled()) {                     
                    reqlogger.info("Reply to first " + tmp.getAddress() + ":" 
                            + tmp.getPort());
                }
                
                tmp = (InetSocketAddress) info.s.getRemoteSocketAddress();

                out.writeUTF(tmp.getAddress().toString());
                out.writeInt(tmp.getPort());
                out.flush();

                if (reqlogger.isInfoEnabled()) {                     
                    reqlogger.info("Reply to second " + tmp.getAddress() + ":" 
                            + tmp.getPort());
                }
                
            } catch (Exception e) {                
                // The connections may have been closed already....
                if (reqlogger.isInfoEnabled()) {                     
                    reqlogger.info("Failed to forward splice info!", e);
                }
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
                synchronized (spliceInfo) {
                    Iterator itt = spliceInfo.keySet().iterator();
                    LinkedList<String> garbage = new LinkedList<String>();
                    
                    while (itt.hasNext()) { 
                        String key = (String) itt.next();                   
                        SpliceInfo tmp = (SpliceInfo) spliceInfo.get(key);
                    
                        if (tmp.bestBefore < now) { 
                            garbage.add(key);
                        } else { 
                            if (tmp.bestBefore < nextInvalidation) { 
                                nextInvalidation = tmp.bestBefore;
                            } 
                        } 
                    }
                
                    for (String g: garbage) { 
                        spliceInfo.remove(g);
                    }
                    
                    if (spliceInfo.size() == 0) { 
                        nextInvalidation = -1;
                    }
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

        hublogger.debug("Waiting for connection...");
        
        try {
            s = server.accept();     
            s.setTcpNoDelay(true);
            s.setSendBufferSize(256*1024);
            s.setReceiveBufferSize(256*1024);
            
            if (hconlogger.isInfoEnabled()) {
                hconlogger.info("Acceptor send buffer = " + s.getSendBufferSize());
                hconlogger.info("Acceptor recv buffer = " + s.getReceiveBufferSize());
            }       
             
            in = new DataInputStream(
                    new BufferedInputStream(s.getInputStream()));

            out = new DataOutputStream(
                    new BufferedOutputStream(s.getOutputStream()));

            int opcode = in.read();

            switch (opcode) {
            case HubProtocol.CONNECT:
                result = handleIncomingHubConnect(s, in, out);                   
                break;

            case HubProtocol.PING:                
                result = handlePing(s, in, out);                   
                break;
              
            case HubProtocol.SERVICELINK_CONNECT:
                result = handleServiceLinkConnect(s, in, out);
                break;                

            //case HubProtocol.BOUNCE_IP:
            //    result = handleBounce(s, in, out);
            //    break;                
            
            case HubProtocol.GET_SPLICE_INFO:
                result = handleSpliceInfo(s, in, out);
                break;                
                            
            default:
                break;
            }
        } catch (Exception e) {
            hublogger.warn("Failed to accept connection!", e);
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
