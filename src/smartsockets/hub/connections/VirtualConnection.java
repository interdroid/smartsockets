package smartsockets.hub.connections;

public class VirtualConnection {
    
    // The connection that created this virtual connection
    public final String key1;
    public final MessageForwardingConnection mfc1;
    public final long index1;

    // The connection that we will forward messages to...
    public final String key2;
    public final MessageForwardingConnection mfc2;
    public final long index2;
    
    private int maxCredits;    
    private int credits;
    
    private long bytes_1_to_2;
    private long bytes_2_to_1;
    
    private boolean connected = false;
    private boolean removing = false;
    
    VirtualConnection(MessageForwardingConnection mfc1, String key1, long index1, 
            MessageForwardingConnection mfc2, String key2, long index2) {  
      
        this.key1 = key1;
        this.mfc1 = mfc1;
        this.index1 = index1;

        this.key2 = key2;
        this.mfc2 = mfc2;
        this.index2 = index2;
    }

    /*
    public void init(int credits) { 
        this.target = null;
        this.maxCredits = this.credits = credits;
        bytesSend = bytesReceived = 0;  
    }
    */
    
    
   
    public void remove() { 
        
        synchronized (this) {
            
            if (removing) {
                // The 'other' owner is already removing this connection!
                return;
            }
            
        }
        
        
    }
    
    
    /*
    public synchronized boolean close() {
        boolean prev = closed;         
        closed = true;
        return prev;
    }
    
    public synchronized boolean isClosed() {
        return closed;
    }
        
    public synchronized void getCredit() {
        
        while (credits == 0) { 
            try { 
                wait();
            } catch (Exception e) {
                // ignore
            }
        }
        
        credits--;
    }
    
    public synchronized void addCredit() {        
        credits++;
        notifyAll();
        
        if (credits > maxCredits) { 
            System.err.println("EEK: exceeded max credits!");
        }
    }
    
    */
} 
   
