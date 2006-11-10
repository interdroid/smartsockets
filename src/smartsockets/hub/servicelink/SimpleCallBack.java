package smartsockets.hub.servicelink;

public class SimpleCallBack {

    private boolean haveResult = false;
    private Object result; 
    
    public synchronized Object getReply() {
        
        while (!haveResult) { 
            try { 
                wait();
            } catch (InterruptedException e) {
                // ignore
            }
        }
        
        return result;
    } 
        
    public synchronized void storeReply(Object result) {           
        this.result = result;
        haveResult = true;
        notifyAll();
    }   
}

