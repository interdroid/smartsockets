package ibis.smartsockets.hub.state;

import ibis.smartsockets.direct.DirectSocketAddress;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;

public class ClientDescription {

    final DirectSocketAddress clientAddress;
    
    private long version = 0;    
    private HashMap<String, String> services;
    
    public ClientDescription(DirectSocketAddress clientAddress) { 
        this.clientAddress = clientAddress;
    }
    
    private ClientDescription(DirectSocketAddress clientAddress, long version, 
            HashMap<String, String> services) {
        
        this.clientAddress = clientAddress;
        this.version = version;
        this.services = services;
    }
        
    protected boolean addService(String tag, String info) { 
        if (services == null) { 
            services = new HashMap<String, String>();
        }
        
        if (services.containsKey(tag)) { 
            return false;
        }
        
        services.put(tag, info);        
        version++;        
        return true;        
    }
    
    protected boolean updateService(String tag, String info) { 
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
    
    
    protected synchronized boolean removeService(String tag) { 
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

    protected boolean containsService(String tag) { 
        
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
            for (String key : services.keySet()) { 

                String val = services.get(key);
                
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
/*
    protected void update(ClientDescription c) {

        if (c.version <= version) { 
            return;
        }

        version = c.version;
        services = c.services;
    }
  */  
    
    public boolean equals(Object other) { 
        if (!(other instanceof ClientDescription)) {
            return false;
        } 
        
        return clientAddress.equals(((ClientDescription) other).clientAddress);
    }
    
    public int hashCode() { 
        return clientAddress.hashCode();
    }
    
    public void write(DataOutputStream out) throws IOException { 
        
        // TODO: is there a race condition here ??? The values below may change 
        // while we are writing the object, but if we synchronize the lot, we 
        // may get a deadlock if the streams block...             
        out.writeUTF(clientAddress.toString());
        out.writeLong(version);
        
        if (services == null) { 
            out.writeInt(0);                
        } else {
            out.writeInt(services.size());
            
            for (String key : services.keySet()) { 
                String value = services.get(key);
                
                if (value == null) { 
                    value = "";                   
                }
                
                out.writeUTF(key);
                out.writeUTF(value);
            }                       
        } 
            
    }
    
    public static ClientDescription read(DataInputStream in) throws IOException { 
        
        DirectSocketAddress adress = DirectSocketAddress.getByAddress(in.readUTF());
        long version = in.readLong();                      
        int services = in.readInt();
        
        HashMap<String, String> m = new HashMap<String, String>();
        
        for (int s=0;s<services;s++) {
            m.put(in.readUTF(), in.readUTF());
        }
        
        return new ClientDescription(adress, version, m);
    }
}
