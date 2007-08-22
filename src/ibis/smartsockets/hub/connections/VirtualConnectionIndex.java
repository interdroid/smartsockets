package ibis.smartsockets.hub.connections;

public class VirtualConnectionIndex {

    // Fix: made sure that it either stays even or uneven. --Ceriel
    // TODO: protect against wrap-around. Just lose the %-operation in
    // nextIndex??? This would at least postpone the wrap-around for quite
    // a while :-)

    private long nextIndex = 0;
    
    public VirtualConnectionIndex(boolean even) { 
    
        int rnd = (int) Math.round(Math.random() * (Integer.MAX_VALUE-1)); 
        
        if (even) {
            if (rnd % 2 != 0) { 
                rnd++;
            }
        } else { 
            if (rnd % 2 != 1) { 
                rnd++;
            }
        }

        nextIndex = rnd % (Integer.MAX_VALUE - 1);
    }
    
    // Made synchronized --Ceriel
    public synchronized long nextIndex() { 
        long result = nextIndex;
        nextIndex = (nextIndex + 2) % (Integer.MAX_VALUE - 1);
        return result;
    }
    
}
