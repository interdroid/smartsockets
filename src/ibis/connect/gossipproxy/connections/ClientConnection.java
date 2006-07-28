package ibis.connect.gossipproxy.connections;

import ibis.connect.direct.DirectSocket;
import ibis.connect.direct.DirectSocketFactory;
import ibis.connect.gossipproxy.ClientDescription;
import ibis.connect.gossipproxy.ProxyDescription;
import ibis.connect.gossipproxy.ProxyList;
import ibis.connect.gossipproxy.ServiceLinkProtocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;

public class ClientConnection extends MessageForwardingConnection {

    private final String clientAddress;
    
    public ClientConnection(String clientAddress, DirectSocket s, 
            DataInputStream in, DataOutputStream out, Connections connections,
            ProxyList proxies) {
     
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

        forwardClientMessage(clientAddress, target, module, code, message);
    } 
                
    private void disconnect() {
        
        if (knownProxies.getLocalDescription().removeClient(clientAddress)) { 
            logger.debug("Removed client " + clientAddress + " from local proxy"); 
        } else { 
            logger.debug("Failed to removed client " + clientAddress + " from local proxy");
        }
        
        connections.removeConnection(clientAddress);
        DirectSocketFactory.close(s, out, in);            
    } 
    
    synchronized boolean sendMessage(String src, String module, int code, 
            String message) {  
        
        try { 
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
        
    private void proxies() throws IOException { 
        
        String id = in.readUTF();
        
        logger.debug("Connection " + clientAddress + " return id: " + id); 
        
        String [] proxies = knownProxies.proxiesAsString();

        out.write(ServiceLinkProtocol.INFO);           
        out.writeUTF(id);            
        out.writeInt(proxies.length);

        for (int i=0;i<proxies.length;i++) { 
            out.writeUTF(proxies[i]);
        } 
            
        out.flush();        
    } 
    
    private void clientsForProxy() throws IOException { 
        String id = in.readUTF();
        String proxy = in.readUTF();
        String tag = in.readUTF();
        
        logger.debug("Connection " + clientAddress + " return id: " + id); 
        
        ProxyDescription p = knownProxies.get(proxy); 
        
        ArrayList tmp = null;
        
        if (p != null) {
            tmp = p.getClients(tag);      
        } else { 
            tmp = new ArrayList();
        }
        
        out.write(ServiceLinkProtocol.INFO);           
        out.writeUTF(id);            
        out.writeInt(tmp.size());

        for (int i=0;i<tmp.size();i++) {
            out.writeUTF(((ClientDescription) tmp.get(i)).toString());
        } 
            
        out.flush();        
    } 

    private void clients() throws IOException { 
        
        String id = in.readUTF();
        String tag = in.readUTF();
        
        logger.debug("Connection " + clientAddress + " return id: " + id); 
        
        ArrayList clients = knownProxies.allClients(tag);

        out.write(ServiceLinkProtocol.INFO);           
        out.writeUTF(id);            
        out.writeInt(clients.size());

        logger.debug("Connection " + clientAddress + " returning : " 
                + clients.size() + " clients");         
        
        for (int i=0;i<clients.size();i++) {
            out.writeUTF(((ClientDescription) clients.get(i)).toString());
        } 
            
        out.flush();        

    } 

    private void directions() throws IOException { 
        String id = in.readUTF();
        String client = in.readUTF();
                
        logger.debug("Connection " + clientAddress + " return id: " + id); 
        
        LinkedList result = knownProxies.directionToClient(client);
        
        out.write(ServiceLinkProtocol.INFO);           
        out.writeUTF(id);            
        out.writeInt(result.size());

        logger.debug("Connection " + clientAddress + " returning : " 
                + result.size() + " possible directions!");         
        
        Iterator itt = result.iterator();
       
        while (itt.hasNext()) {             
            String tmp = (String) itt.next();                       
            logger.debug(" -> " + tmp);                             
            out.writeUTF(tmp);
        } 
            
        out.flush();        
    } 
    
    private void registerService() throws IOException { 
        
        String id = in.readUTF();
        String tag = in.readUTF();
        String address = in.readUTF();

        logger.debug("Connection " + clientAddress + " return id: " + id +  
                " adding " + tag + " " + address + " to services!");         
               
        ProxyDescription localProxy = knownProxies.getLocalDescription();
        
        out.write(ServiceLinkProtocol.INFO);           
        out.writeUTF(id);            
        out.writeInt(1);
        
        if (localProxy.addService(clientAddress, tag, address)) { 
            out.writeUTF("OK");
        } else { 
            out.writeUTF("DENIED");
        }
            
        out.flush();        
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
            
            case ServiceLinkProtocol.PROXIES:
                if (logger.isDebugEnabled()) {
                    logger.debug("Connection " + clientAddress + " requests " 
                            + "proxies");
                } 
                proxies();
                return true;
            
            case ServiceLinkProtocol.CLIENTS_FOR_PROXY:
                if (logger.isDebugEnabled()) {
                    logger.debug("Connection " + clientAddress + " requests" 
                            + " local clients");
                } 
                clientsForProxy();
                return true;
            
            case ServiceLinkProtocol.ALL_CLIENTS:
                if (logger.isDebugEnabled()) {
                    logger.debug("Connection " + clientAddress + " requests" 
                            + " all clients");
                }
                clients();
                return true;
            
            case ServiceLinkProtocol.DIRECTION:
                if (logger.isDebugEnabled()) {
                    logger.debug("Connection " + clientAddress + " requests" 
                            + " direction to other client");
                }
                directions();
                return true;
            
            case ServiceLinkProtocol.REGISTER_SERVICE:
                if (logger.isDebugEnabled()) {
                    logger.debug("Connection " + clientAddress + " requests" 
                            + " service registration");
                }
                registerService();
                return true;
                
            default:
                logger.warn("Connection " + clientAddress + " got unknown "
                        + "opcode " + opcode + " -- disconnecting");
                disconnect();
                return false;                
            } 
            
        } catch (Exception e) { 
            logger.warn("Connection to " + clientAddress + " is broken!", e);
            disconnect();
        }
        
        return false;
    }
}
