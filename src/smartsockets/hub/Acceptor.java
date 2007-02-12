package smartsockets.hub;


import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;

import org.apache.log4j.Logger;

import smartsockets.Properties;
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
import smartsockets.util.TypedProperties;

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
        
    private int sendBuffer = -1;
    private int receiveBuffer = -1;
    
    Acceptor(TypedProperties p, int port, StateCounter state, 
            Map<SocketAddressSet, BaseConnection> connections, 
            HubList knownProxies, VirtualConnections vcs,
            DirectSocketFactory factory) throws IOException {

        super("HubAcceptor", state, connections, knownProxies, vcs, factory);        

        sendBuffer = p.getIntProperty(Properties.HUB_SEND_BUFFER, -1);
        receiveBuffer = p.getIntProperty(Properties.HUB_RECEIVE_BUFFER, -1);
        
        // NOTE: the receivebuffer must be passed to the serversocket to have 
        // any effect on the sockets that are accepted later...
        server = factory.createServerSocket(port, 50, receiveBuffer, null);        
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
                      
            out.write(ConnectionProtocol.CONNECTION_REFUSED);
            out.flush();
            return false;
        } else {                                     
            // We just created a connection to this hub.
          //  if (hconlogger.isInfoEnabled()) {
                hconlogger.warn("Connection accepted from hub " + addr);
           // }

            out.write(ConnectionProtocol.CONNECTION_ACCEPTED);            
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

                out.write(ConnectionProtocol.CONNECTION_REFUSED);
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

            out.write(ConnectionProtocol.CONNECTION_ACCEPTED);
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
        
        if (reqlogger.isInfoEnabled()) { 
            reqlogger.info("Got request for splice info");
        }
        
        try { 
            DirectSimpleSocket dss = (DirectSimpleSocket) s;            
        
            InetSocketAddress tmp = 
                (InetSocketAddress) dss.getRemoteSocketAddress();

            out.writeUTF(tmp.getAddress().toString());
            out.writeInt(tmp.getPort());
            out.flush();
            
            if (reqlogger.isInfoEnabled()) {                     
                reqlogger.info("Reply to splice info request " 
                        + tmp.getAddress() + ":" + tmp.getPort());
            }
                
        } catch (Exception e) {                
            // The connections may have been closed already....
            if (reqlogger.isInfoEnabled()) {                     
                reqlogger.info("Failed to forward splice info!", e);
            }
        } 
        
        return false;
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
            
            if (sendBuffer > 0) { 
                s.setSendBufferSize(sendBuffer);
            }
            
            if (receiveBuffer > 0) { 
                s.setReceiveBufferSize(receiveBuffer);
            }
            
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
            case ConnectionProtocol.HUB_CONNECT:
                result = handleIncomingHubConnect(s, in, out);                   
                break;

            case ConnectionProtocol.PING:                
                result = handlePing(s, in, out);                   
                break;
              
            case ConnectionProtocol.SERVICELINK_CONNECT:
                result = handleServiceLinkConnect(s, in, out);
                break;                
            
            case ConnectionProtocol.GET_SPLICE_INFO:
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
