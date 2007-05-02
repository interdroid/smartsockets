package ibis.smartsockets.hub.state;

public class StateCounter {

    // NOTE: we initialize the local state counter with the current time, and
    // afterward increment by 1 each time a significant state change occurs in 
    // the local hub. Assuming that the frequency of state changes is lower than 
    // the clock frequency of the machine, this will ensure that a hub that 
    // restarts after a crash will alway have a higher state number than it's 
    // crashed predecessor. Therefore hubs can safely be restarted without 
    // corrupting the state stored in it's peers. 
    private long state = System.currentTimeMillis();
    
    public synchronized long get() { 
        return state;
    }
    
    public synchronized long increment() { 
        return ++state;
    }        
}
