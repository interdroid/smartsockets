package smartsockets.hub.connections;


import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import org.apache.log4j.Logger;

import com.sun.org.apache.bcel.internal.generic.InstructionConstants.Clinit;

import smartsockets.direct.DirectSocket;
import smartsockets.direct.DirectSocketFactory;
import smartsockets.direct.SocketAddressSet;
import smartsockets.hub.servicelink.ServiceLinkProtocol;
import smartsockets.hub.state.AddressAsStringSelector;
import smartsockets.hub.state.ClientDescription;
import smartsockets.hub.state.ClientsByTagAsStringSelector;
import smartsockets.hub.state.DetailsSelector;
import smartsockets.hub.state.DirectionsAsStringSelector;
import smartsockets.hub.state.HubDescription;
import smartsockets.hub.state.HubList;

public class ClientConnection extends MessageForwardingConnection {

    protected static Logger conlogger = 
        ibis.util.GetLogger.getLogger("smartsockets.hub.connections.client"); 
    
    protected static Logger reqlogger = 
        ibis.util.GetLogger.getLogger("smartsockets.hub.request"); 
    
    protected static Logger reglogger = 
        ibis.util.GetLogger.getLogger("smartsockets.hub.registration"); 
        
    private final SocketAddressSet clientAddress;
    
    public ClientConnection(SocketAddressSet clientAddress, DirectSocket s, 
            DataInputStream in, DataOutputStream out, 
            Map<SocketAddressSet, BaseConnection> connections,
            HubList hubs) {
     
        super(s, in, out, connections, hubs);        
        this.clientAddress = clientAddress;
        
        if (conlogger.isDebugEnabled()) {
            conlogger.debug("Created client connection: " + clientAddress);
        }
    }

    private void handleMessage() throws IOException { 
        // Read the message
        
        ClientMessage cm = new ClientMessage(clientAddress, 
                knownHubs.getLocalDescription().hubAddress, 0, in);
        
        if (meslogger.isDebugEnabled()) {
            meslogger.debug("Incoming message: " + cm);
        }
        
        forward(cm, true);
    } 
                
    private void disconnect() {
        
        if (knownHubs.getLocalDescription().removeClient(clientAddress)) {
            if (conlogger.isDebugEnabled()) {
                conlogger.debug("Removed client connection " + clientAddress);
            }
        } else if (conlogger.isDebugEnabled()) {
                conlogger.debug("Failed to removed client connection " 
                        + clientAddress + "!");
        }
        
        connections.remove(clientAddress);
        DirectSocketFactory.close(s, out, in);            
    } 
    
    synchronized boolean sendMessage(ClientMessage m) {  
        
        try { 
            out.write(ServiceLinkProtocol.MESSAGE);            
            m.writePartially(out);
            out.flush();
            return true;
        } catch (IOException e) {            
            meslogger.warn("Connection " + clientAddress + " is broken!", e);
            DirectSocketFactory.close(s, out, in);
            return false;                
        }
    }
        
    private void hubs() throws IOException { 
        
        String id = in.readUTF();
        
        if (reqlogger.isDebugEnabled()) {
            reqlogger.debug("Connection " + clientAddress + " return id: " + id);
        }
                
        AddressAsStringSelector as = new AddressAsStringSelector();
        
        knownHubs.select(as);
                
        LinkedList<String> result = as.getResult();
        
        out.write(ServiceLinkProtocol.INFO);           
        out.writeUTF(id);            
        out.writeInt(result.size());
        
        for (String s : result) {  
            out.writeUTF(s);
        } 
            
        out.flush();        
    } 

    private void hubDetails() throws IOException { 
        
        String id = in.readUTF();
        
        if (reqlogger.isDebugEnabled()) {
            reqlogger.debug("Connection " + clientAddress + " return id: " + id);
        }
        
        DetailsSelector as = new DetailsSelector();
        
        knownHubs.select(as);
        
        LinkedList<String> result = as.getResult();
        
        out.write(ServiceLinkProtocol.INFO);           
        out.writeUTF(id);            
        out.writeInt(result.size());

        if (reqlogger.isDebugEnabled()) {
            reqlogger.debug("Connection " + clientAddress + " result: " 
                    + result.size() + " " + result);
        }
        
        for (String s : result) { 
            out.writeUTF(s);
        } 
            
        out.flush();        
    } 

    
    private void clientsForHub() throws IOException { 
        String id = in.readUTF();
        String hub = in.readUTF();
        String tag = in.readUTF();
        
        if (reqlogger.isDebugEnabled()) {
            reqlogger.debug("Connection " + clientAddress + " return id: " + id); 
        }
        
        LinkedList<String> result = new LinkedList<String>();
        
        try { 
            HubDescription d = knownHubs.get(new SocketAddressSet(hub));            
            d.getClientsAsString(result, tag);            
        } catch (UnknownHostException e) {
            reqlogger.warn("Connection " + clientAddress + " got illegal hub " 
                    + "address: " + hub); 
        }
               
        out.write(ServiceLinkProtocol.INFO);           
        out.writeUTF(id);            
        out.writeInt(result.size());

        for (String s : result) { 
            out.writeUTF(s);
        } 
            
        out.flush();        
    } 

    private void clients() throws IOException { 
        
        String id = in.readUTF();
        String tag = in.readUTF();
        
        if (reqlogger.isDebugEnabled()) {
            reqlogger.debug("Connection " + clientAddress + " return id: " + id);
        }
        
        ClientsByTagAsStringSelector css = new ClientsByTagAsStringSelector(tag);
        
        knownHubs.select(css);
        
        LinkedList<String> result = css.getResult();

        out.write(ServiceLinkProtocol.INFO);           
        out.writeUTF(id);            
        out.writeInt(result.size());

        if (reqlogger.isDebugEnabled()) {
            reqlogger.debug("Connection " + clientAddress + " returning : " 
                    + result.size() + " clients: " + result);
        }
        
        for (String s : result) {
            out.writeUTF(s);
        } 
            
        out.flush();        

    } 

    private void directions() throws IOException { 
        String id = in.readUTF();
        String client = in.readUTF();
         
        if (reqlogger.isDebugEnabled()) {
            reqlogger.debug("Connection " + clientAddress + " return id: " + id);
        }
        
        DirectionsAsStringSelector ds = 
            new DirectionsAsStringSelector(new SocketAddressSet(client));
        
        knownHubs.select(ds);
        
        LinkedList<String> result = ds.getResult();
        
        out.write(ServiceLinkProtocol.INFO);           
        out.writeUTF(id);            
        out.writeInt(result.size());

        if (reqlogger.isDebugEnabled()) {
            reqlogger.debug("Connection " + clientAddress + " returning : " 
                    + result.size() + " possible directions: " + result);
        }
        
        for (String tmp : result) { 
            out.writeUTF(tmp);
        } 
            
        out.flush();        
    } 
    
    private void registerInfo() throws IOException { 
        
        String id = in.readUTF();
        String tag = in.readUTF();
        String info = in.readUTF();

        if (reqlogger.isDebugEnabled()) {
            reglogger.debug("Connection " + clientAddress + " return id: " + id +  
                    " adding info: " + tag + " " + info);
        }
               
        HubDescription localHub = knownHubs.getLocalDescription();
        
        out.write(ServiceLinkProtocol.INFO);           
        out.writeUTF(id);            
        out.writeInt(1);
        
        if (localHub.addService(clientAddress, tag, info)) { 
            out.writeUTF("OK");
        } else { 
            out.writeUTF("DENIED");
        }
            
        out.flush();        
    } 

    private void updateInfo() throws IOException { 
        
        String id = in.readUTF();
        String tag = in.readUTF();
        String info = in.readUTF();

        if (reqlogger.isDebugEnabled()) {
            reglogger.debug("Connection " + clientAddress + " return id: " + id +  
                    " updating info: " + tag + " " + info);         
        }
        
        HubDescription localHub = knownHubs.getLocalDescription();
        
        out.write(ServiceLinkProtocol.INFO);           
        out.writeUTF(id);            
        out.writeInt(1);
        
        if (localHub.updateService(clientAddress, tag, info)) { 
            out.writeUTF("OK");
        } else { 
            out.writeUTF("DENIED");
        }
            
        out.flush();        
    } 

    private void removeInfo() throws IOException { 
        
        String id = in.readUTF();
        String tag = in.readUTF();
        
        if (reqlogger.isDebugEnabled()) {
            reglogger.debug("Connection " + clientAddress + " return id: " + id +  
                    " removing info: " + tag);
        }
               
        HubDescription localHub = knownHubs.getLocalDescription();
        
        out.write(ServiceLinkProtocol.INFO);           
        out.writeUTF(id);            
        out.writeInt(1);
        
        if (localHub.removeService(clientAddress, tag)) { 
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
                if (meslogger.isDebugEnabled()) {
                    meslogger.debug("Connection " + clientAddress + " got message");
                }                     
                handleMessage();
                return true;
                
            case ServiceLinkProtocol.DISCONNECT:
                if (conlogger.isDebugEnabled()) {
                    conlogger.debug("Connection " + clientAddress + " disconnecting");
                } 
                disconnect();
                return false;
            
            case ServiceLinkProtocol.HUBS:
                if (reqlogger.isDebugEnabled()) {
                    reqlogger.debug("Connection " + clientAddress + " requests " 
                            + "hubs");
                } 
                hubs();
                return true;

            case ServiceLinkProtocol.HUB_DETAILS:
                if (reqlogger.isDebugEnabled()) {
                    reqlogger.debug("Connection " + clientAddress + " requests " 
                            + "hub details");
                } 
                hubDetails();
                return true;
                
            case ServiceLinkProtocol.CLIENTS_FOR_HUB:
                if (reqlogger.isDebugEnabled()) {
                    reqlogger.debug("Connection " + clientAddress + " requests" 
                            + " local clients");
                } 
                clientsForHub();
                return true;
            
            case ServiceLinkProtocol.ALL_CLIENTS:
                if (reqlogger.isDebugEnabled()) {
                    reqlogger.debug("Connection " + clientAddress + " requests" 
                            + " all clients");
                }
                clients();
                return true;
            
            case ServiceLinkProtocol.DIRECTION:
                if (reqlogger.isDebugEnabled()) {
                    reqlogger.debug("Connection " + clientAddress + " requests" 
                            + " direction to other client");
                }
                directions();
                return true;
            
            case ServiceLinkProtocol.REGISTER_PROPERTY:
                if (reglogger.isDebugEnabled()) {
                    reglogger.debug("Connection " + clientAddress + " requests" 
                            + " info registration");
                }
                registerInfo();
                return true;
            
            case ServiceLinkProtocol.UPDATE_PROPERTY:
                if (reglogger.isDebugEnabled()) {
                    reglogger.debug("Connection " + clientAddress + " requests" 
                            + " info update");
                }
                updateInfo();
                return true;
            
            case ServiceLinkProtocol.REMOVE_PROPERTY:
                if (reglogger.isDebugEnabled()) {
                    reglogger.debug("Connection " + clientAddress + " requests" 
                            + " info removal");
                }
                removeInfo();
                return true;
                            
            default:
                conlogger.warn("Connection " + clientAddress 
                        + " got unknown " + "opcode " + opcode 
                        + " -- disconnecting");
                disconnect();
                return false;                
            } 
            
        } catch (Exception e) { 
            conlogger.warn("Connection to " + clientAddress + " is broken!", e);
            disconnect();
        }
        
        return false;
    }
}
