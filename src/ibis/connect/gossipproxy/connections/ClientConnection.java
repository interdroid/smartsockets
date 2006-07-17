package ibis.connect.gossipproxy.connections;

import ibis.connect.direct.DirectSocket;
import ibis.connect.direct.DirectSocketFactory;
import ibis.connect.gossipproxy.ProxyList;
import ibis.connect.gossipproxy.ServiceLinkProtocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class ClientConnection extends MessageForwardingConnection {

    private final String clientAddress;
    
    public ClientConnection(String clientAddress, DirectSocket s, DataInputStream in, 
            DataOutputStream out, Connections connections, ProxyList proxies) {
     
        super(s, in, out, connections, proxies);        
        this.clientAddress = clientAddress;
    }

    private void handleMessage() throws IOException { 
        // Read the message
        String target = in.readUTF();                    
        String module = in.readUTF();
        int code = in.readInt();
        String message = in.readUTF();
        
        logger.debug("Incoming message: [" + target + ", " 
                + module + ", " + code + ", " + message); 

        forwardMessage(clientAddress, target, module, code, message);
    } 
                
    private void disconnect() { 
        connections.removeConnection(clientAddress);
        DirectSocketFactory.close(s, out, in);            
    } 
    
    synchronized boolean sendMessage(String src, String module, int code, 
            String message) {  
        
        try{ 
            out.write(ServiceLinkProtocol.MESSAGE);
            out.writeUTF(src);
            out.writeUTF(module);
            out.writeInt(code);
            out.writeUTF(message);
            out.flush();
            return true;
        } catch (IOException e) {
            logger.warn("Connection " + src + " is broken!", e);
            DirectSocketFactory.close(s, out, in);
            return false;                
        }
    }
        
    protected String getName() {
        return "ServiceLink(" + clientAddress + ")";
    }

    protected boolean runConnection() {           
                     
        try { 
            int opcode = in.read();

            switch (opcode) { 
            case ServiceLinkProtocol.MESSAGE:
                if (logger.isDebugEnabled()) {
                    logger.debug("Connection " + clientAddress + " got message");
                }                     
                handleMessage();
                return true;
                
            case ServiceLinkProtocol.DISCONNECT:
                if (logger.isDebugEnabled()) {
                    logger.debug("Connection " + clientAddress + " disconnecting");
                } 
                disconnect();
                return false;
                
            default:
                logger.warn("Connection " + clientAddress + " got unknown opcode" 
                        + opcode + " -- disconnecting");
                disconnect();
                return false;                
            } 
            
        } catch (Exception e) { 
            logger.warn("Connection to " + clientAddress + " is broken!", e);
            DirectSocketFactory.close(s, out, in);            
        }
        
        return false;
    }
}
