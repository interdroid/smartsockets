package smartsockets.hub.state;

public class StateCounter {

    private long state = 0;
    
    public synchronized long get() { 
        return state;
    }
    
    public synchronized long increment() { 
        return ++state;
    }        
}
