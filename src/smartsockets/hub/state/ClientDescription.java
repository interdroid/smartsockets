package smartsockets.hub.state;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import smartsockets.direct.SocketAddressSet;

public class ClientDescription {

    long version = 0;    
    final SocketAddressSet clientAddress;
    HashMap services;
    
    public ClientDescription(SocketAddressSet clientAddress) { 
        this.clientAddress = clientAddress;
    }
    
    private ClientDescription(SocketAddressSet clientAddress, long version, 
            HashMap services) {
        
        this.clientAddress = clientAddress;
        this.version = version;
        this.services = services;
    }
        
    public boolean addService(String tag, String info) { 
        if (services == null) { 
            services = new HashMap();
        }
        
        if (services.containsKey(tag)) { 
            return false;
        }
        
        services.put(tag, info);        
        version++;        
        return true;        
    }
    
    public boolean updateService(String tag, String info) { 
        if (services == null) {
            return false;
        }
        
        if (!services.containsKey(tag)) { 
            return false;
        }
        
        // Overrides previous version...
        services.put(tag, info);        
        version++;        
        return true;        
    }
    
    
    public boolean removeService(String tag) { 
        if (services == null) { 
            return false;
        }
        
        if (!services.containsKey(tag)) {
            return false;
        }
        
        services.remove(tag);
        version++;
        return true;        
    }

    public boolean containsService(String tag) { 
        
        if (tag == null || tag.length() == 0) { 
            return true;
        }
        
        if (services == null) { 
            return false;
        }
        
        return services.containsKey(tag);
    }
    
    public String toString() { 
        StringBuffer tmp = new StringBuffer("Client(");
        
        tmp.append(clientAddress);        
        tmp.append(", ");
        tmp.append(version);
                
        if (services != null) { 
            Iterator itt = services.keySet().iterator();
            
            while (itt.hasNext()) { 
                String key = (String) itt.next();
                String val = (String) services.get(key);
                
                tmp.append(", [");
                tmp.append(key);
                tmp.append(",");
                tmp.append(val);
                tmp.append("]");
            }
        }

        tmp.append(")");
        
        return tmp.toString();
    }

    public void update(ClientDescription c) {

        if (c.version <= version) { 
            return;
        }

        version = c.version;
        services = c.services;
    }
    
    public boolean equals(Object other) { 
        if (!(other instanceof ClientDescription)) {
            return false;
        } 
        
        return clientAddress.equals(((ClientDescription) other).clientAddress);
    }
    
    public int hashCode() { 
        return clientAddress.hashCode();
    }
    
    public static void write(ClientDescription c, DataOutputStream out) throws IOException { 
        
        out.writeUTF(c.clientAddress.toString());
        out.writeLong(c.version);
        
        if (c.services == null) { 
            out.writeInt(0);                
        } else {         
            
            Collection ser = c.services.entrySet();
            
            out.writeInt(ser.size());
        
            Iterator itt = ser.iterator();
            
            while (itt.hasNext()) { 
                Map.Entry entry = (Map.Entry) itt.next();                
                out.writeUTF((String) entry.getKey());
                out.writeUTF((String) entry.getValue());
            }           
        } 
            
    }
    
    public static ClientDescription read(DataInputStream in) throws IOException { 
        
        SocketAddressSet adress = new SocketAddressSet(in.readUTF());
        long version = in.readLong();                      
        int services = in.readInt();
        
        HashMap m = new HashMap();
        
        for (int s=0;s<services;s++) {
            m.put(in.readUTF(), in.readUTF());
        }
        
        return new ClientDescription(adress, version, m);
    }
}
