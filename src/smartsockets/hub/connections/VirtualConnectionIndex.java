package smartsockets.hub.connections;

public class VirtualConnectionIndex {

    private long nextIndex = 0;
    
    public VirtualConnectionIndex(boolean even) { 
        if (even) {
            nextIndex = 0;
        } else { 
            nextIndex = 1;
        }
    }
    
    public long nextIndex() { 
        long result = nextIndex;
        nextIndex += 2;
        return result;
    }
    
}
