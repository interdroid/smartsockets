package smartsockets.hub.connections;

public class VirtualConnection {
    
    public final int index;

    private int maxCredits;    
    private int credits;
    
    private long bytesSend;
    private long bytesReceived;
    
    private boolean closed = false;
    
    protected MessageForwardingConnection targetConnection;
    protected int targetConnectionIndex;
    
    VirtualConnection(int number) { 
        this.index = number;
    }

    public void init(int credits) { 
        this.targetConnection = null;
        this.maxCredits = this.credits = credits;
        bytesSend = bytesReceived = 0;  
    }
    
    public void init(MessageForwardingConnection nextHop, int nextVC, int credits) { 
        this.targetConnection = nextHop;
        this.targetConnectionIndex = nextVC;
        this.maxCredits = this.credits = credits;
        bytesSend = bytesReceived = 0;  
    }

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
    
} 
   
