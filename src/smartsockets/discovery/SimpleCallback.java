package smartsockets.discovery;

import java.util.LinkedList;

import org.apache.log4j.Logger;

public class SimpleCallback implements Callback {
    
    private static final Logger logger = 
        ibis.util.GetLogger.getLogger("smartsockets.discovery");
    
    private final LinkedList messages = new LinkedList();    
    private final boolean quitAfterMessage;
            
    public SimpleCallback(boolean quitAfterMessage) { 
        this.quitAfterMessage = quitAfterMessage;        
    }
            
    public synchronized String get(long timeout) { 
        
        long end = System.currentTimeMillis() + timeout;
        long left = timeout;
        
        while (messages.size() == 0) {
            try { 
                wait(left);
            } catch (InterruptedException e) {
                // ignore
            }
            
            left = end - System.currentTimeMillis();
            
            if (timeout > 0 && left <= 0) { 
                return null;
            }                
        }
        
        return (String) messages.removeFirst();
    }
    
    public synchronized boolean gotMessage(String message) {
                      
        if (message != null) {             
            if (logger.isInfoEnabled()) { 
                logger.info("Received: \"" + message + "\"");
            }
            messages.addLast(message);
            notifyAll();
            return !quitAfterMessage;                    
        } else {
            if (logger.isInfoEnabled()) {
                logger.info("Discarding message: \"" + message + "\"");
            }
            return true;
        }
    }          
}