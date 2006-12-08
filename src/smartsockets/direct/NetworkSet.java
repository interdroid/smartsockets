package smartsockets.direct;

import java.net.InetAddress;
import java.net.InetSocketAddress;

public class NetworkSet {

    public final String name;
    
    private final Network [] include;
    private final Network [] exclude;
    
    public NetworkSet(String name, Network [] include, Network [] exclude) {
        
        this.name = name;
        
        if (include.length > 0) {
            this.include = include;
        } else { 
            this.include = null;
        }
        
        if (exclude.length > 0) { 
            this.exclude = exclude;
        } else { 
            this.exclude = null;
        }
    }
     
    public boolean inNetwork(InetAddress[] ads) {
        return ((include == null || inNetwork(include, ads)) && 
                (exclude == null ||!inNetwork(exclude, ads))); 
    }

    public boolean inNetwork(InetSocketAddress[] ads) {
        return ((include == null || inNetwork(include, ads)) && 
                (exclude == null ||!inNetwork(exclude, ads))); 
    }

    private static boolean inNetwork(Network [] nw, InetAddress [] ads) {
        
        for (int i=0;i<nw.length;i++) { 
            if (nw[i].match(ads)) { 
                return true;
            }            
        }
        
        return false;
    }
    
    private static boolean inNetwork(Network [] nw, InetSocketAddress [] ads) {
        
        for (int i=0;i<nw.length;i++) { 
            if (nw[i].match(ads)) { 
                return true;
            }            
        }
        
        return false;
    }
}

