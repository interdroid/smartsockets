package smartsockets.hub.servicelink;

import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import smartsockets.direct.SocketAddressSet;
import smartsockets.virtual.VirtualSocketAddress;


public class Client {

    private final SocketAddressSet clientAddress;
    private final long version;
    private final Map services;
        
    public Client(String clientAsString) { 
    
        final String orig = clientAsString;
        
        System.out.println("Parsing client from string \"" + clientAsString 
                + "\"");
        
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
                new SocketAddressSet(clientAsString.substring(0, index));
        
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
                        
            services = new HashMap();
            
            clientAsString = clientAsString.substring(index);
        
            while (clientAsString.startsWith(", [")) {
                
                clientAsString = clientAsString.substring(3);
                
                index = clientAsString.indexOf(",");
                
                String key = clientAsString.substring(0, index);            
            
                clientAsString = clientAsString.substring(index+1);
            
                index = clientAsString.indexOf(']');
                    
                String value = clientAsString.substring(0, index); 

                services.put(key, value);
            
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
    
    public Client(SocketAddressSet clientAddress, long version, Map services) { 
        this.clientAddress = clientAddress;
        this.version = version;        
        this.services = services;
    }
    
    public SocketAddressSet getClientAddress() { 
        return clientAddress;
    }
    
    public VirtualSocketAddress getServiceAsAddress(String name) 
        throws UnknownHostException {
        
        return new VirtualSocketAddress(getService(name));
    }
    
    public String getService(String name) { 
        return (String) services.get(name);
    }
    
    
    public boolean offersService(String name) { 
        return services.containsKey(name);
    }
          
    public String toString() { 
        
        StringBuffer result = new StringBuffer("Client("); 
        
        result.append(clientAddress.toString());
        
        Iterator it = services.keySet().iterator();
        
        while (it.hasNext()) { 
            String key = (String) it.next();
            String value = (String) services.get(key);            
            
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
