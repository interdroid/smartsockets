package smartsockets.hub.connections;

public class VirtualConnectionIndex {

    private long nextIndex = 0;
    
    
    
    public VirtualConnectionIndex(boolean even) { 
    
        int rnd = (int) Math.round(Math.random() * Integer.MAX_VALUE-1); 
        
        if (even) {
            if (rnd % 2 != 0) { 
                rnd++;
            }
            
            nextIndex = rnd & Integer.MAX_VALUE;
        } else { 
            if (rnd % 2 != 1) { 
                rnd++;
            }
            
            nextIndex = rnd & Integer.MAX_VALUE;
        }
    }
    
    public long nextIndex() { 
        long result = nextIndex;
        nextIndex = (nextIndex + 2) & Integer.MAX_VALUE;
        return result;
    }
    
}
