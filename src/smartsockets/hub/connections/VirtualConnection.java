package smartsockets.hub.connections;

public class VirtualConnection {
    
    public final int number;

    private int maxCredits;    
    private int credits;
    
    private long bytesSend;
    private long bytesReceived;
    
    protected MessageForwardingConnection nextHop;
    protected int nextVC;
    
    VirtualConnection(int number) { 
        this.number = number;
    }

    public void init(int credits) { 
        this.nextHop = null;
        this.maxCredits = this.credits = credits;
        bytesSend = bytesReceived = 0;  
    }
    
    public void init(MessageForwardingConnection nextHop, int nextVC, int credits) { 
        this.nextHop = nextHop;
        this.nextVC = nextVC;
        this.maxCredits = this.credits = credits;
        bytesSend = bytesReceived = 0;  
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
   
