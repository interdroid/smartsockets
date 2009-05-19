package ibis.smartsockets.hub.servicelink;

import ibis.smartsockets.direct.DirectSocketAddress;
import ibis.smartsockets.virtual.VirtualSocketAddress;

import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


public class ClientInfo {

    private final DirectSocketAddress clientAddress;
    private final long version;
    private final Map<String, String> properties;
        
    public ClientInfo(String clientAsString) { 
    
        final String orig = clientAsString;
        
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
                DirectSocketAddress.getByAddress(clientAsString.substring(0, index));
        
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
                        
            properties = new HashMap<String, String>();
            
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
    
    public ClientInfo(DirectSocketAddress clientAddress, long version, 
            Map<String, String> properties) { 
        this.clientAddress = clientAddress;
        this.version = version;        
        this.properties = properties;
    }
    
    public DirectSocketAddress getClientAddress() { 
        return clientAddress;
    }
    
    public VirtualSocketAddress getPropertyAsAddress(String name) 
        throws UnknownHostException {
        
        return new VirtualSocketAddress(getProperty(name));
    }
    
    public String getProperty(String name) { 
        return properties.get(name);
    }
    
    
    public boolean hasProperty(String name) { 
        return properties.containsKey(name);
    }
          
    public String toString() { 
        
        StringBuffer result = new StringBuffer("Client("); 
        
        result.append(clientAddress.toString());
        
        result.append(", "); 
        result.append(version);
        result.append(", "); 
                
        Iterator<?> it = properties.keySet().iterator();
        
        while (it.hasNext()) { 
            String key = (String) it.next();
            String value = properties.get(key);            
            
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
