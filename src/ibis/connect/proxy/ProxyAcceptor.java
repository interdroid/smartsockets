package ibis.connect.proxy;

import ibis.connect.direct.DirectServerSocket;
import ibis.connect.direct.DirectSocket;
import ibis.connect.direct.DirectSocketFactory;
import ibis.connect.direct.SocketAddressSet;
//import ibis.connect.gossipproxy.connections.ForwarderConnection;
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
//import java.util.LinkedList;

public class ProxyAcceptor extends CommunicationThread {

    private DirectServerSocket server;
    private boolean done = false;

    //private int number = 0;

    ProxyAcceptor(StateCounter state, Connections connections, 
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

            out.write(ProxyProtocol.REPLY_CONNECTION_REFUSED);
            out.flush();
            return false;
        } else {                         
            // We just created a connection to this proxy.
            logger.info("Connection from " + addr + " accepted");

            out.write(ProxyProtocol.REPLY_CONNECTION_ACCEPTED);            
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

                out.write(ProxyProtocol.REPLY_SERVICELINK_REFUSED);
                out.flush();
                DirectSocketFactory.close(s, out, in);
                return false;
            }

            if (logger.isDebugEnabled()) { 
                logger.debug("Incoming connection from " + src + " accepted"); 
            } 

            out.write(ProxyProtocol.REPLY_SERVICELINK_ACCEPTED);
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
            case ProxyProtocol.PROXY_CONNECT:
                result = handleIncomingProxyConnect(s, in, out);                   
                break;

            case ProxyProtocol.PROXY_PING:
                result = handlePing(s, in, out);                   
                break;
              
            case ProxyProtocol.PROXY_SERVICELINK_CONNECT:
                result = handleServiceLinkConnect(s, in, out);
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
