package smartsockets.hub.servicelink;

import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import smartsockets.direct.SocketAddressSet;
import smartsockets.virtual.VirtualSocketAddress;


public class ClientInfo {

    private final SocketAddressSet clientAddress;
    private final long version;
    private final Map properties;
        
    public ClientInfo(String clientAsString) { 
    
        final String orig = clientAsString;
        
        /*
        System.out.println("Parsing client from string \"" + clientAsString 
                + "\"");
        */
        
        if (!clientAsString.startsWith("Client(")) { 
            throw new IllegalArgumentException("String does not contain Client" 
                    + " description!");
        }
        
        try { 
            clientAsString = clientAsString.substring(7);
                                         
            int index = clientAsString.indexOf(", ");
        
            if (index == -1) { 
                throw new IllegalArgumentException("String does not contain"
                        + " Client description!");
            }

            clientAddress = 
                SocketAddressSet.getByAddress(clientAsString.substring(0, index));
        
            clientAsString = clientAsString.substring(index+2);
                        
            index = clientAsString.indexOf(", [");

            if (index == -1) {
                index = clientAsString.indexOf(")");                
            }

            if (index == -1) { 
                throw new IllegalArgumentException("String does not contain"
                        + " Client description!");
            }

            version = Long.parseLong(clientAsString.substring(0, index));
                        
            properties = new HashMap();
            
            clientAsString = clientAsString.substring(index);
        
            while (clientAsString.startsWith(", [")) {
                
                clientAsString = clientAsString.substring(3);
                
                index = clientAsString.indexOf(",");
                
                String key = clientAsString.substring(0, index);            
            
                clientAsString = clientAsString.substring(index+1);
            
                index = clientAsString.indexOf(']');
                    
                String value = clientAsString.substring(0, index); 

                properties.put(key, value);
            
                clientAsString = clientAsString.substring(index+1);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("String does not contain Client" 
                    + " description: \"" + orig + "\"", e);

        }            
            
        if (!clientAsString.equals(")")) { 
            throw new IllegalArgumentException("String does not contain Client" 
                    + " description!");
        } 
    } 
    
    public ClientInfo(SocketAddressSet clientAddress, long version, Map properties) { 
        this.clientAddress = clientAddress;
        this.version = version;        
        this.properties = properties;
    }
    
    public SocketAddressSet getClientAddress() { 
        return clientAddress;
    }
    
    public VirtualSocketAddress getPropertyAsAddress(String name) 
        throws UnknownHostException {
        
        return new VirtualSocketAddress(getProperty(name));
    }
    
    public String getProperty(String name) { 
        return (String) properties.get(name);
    }
    
    
    public boolean hasProperty(String name) { 
        return properties.containsKey(name);
    }
          
    public String toString() { 
        
        StringBuffer result = new StringBuffer("Client("); 
        
        result.append(clientAddress.toString());
        
        Iterator it = properties.keySet().iterator();
        
        while (it.hasNext()) { 
            String key = (String) it.next();
            String value = (String) properties.get(key);            
            
            result.append(", [");
            result.append(key);
            result.append(",");
            result.append(value);
            result.append("]");
        }
        
        result.append(")");        
        return result.toString();        
    }    
}
