package smartsockets.hub.servicelink;

public class Credits {

    private final int MAX_CREDITS;
    private int credits;
    
    Credits(int max) { 
        MAX_CREDITS = max;
        credits = max;
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

    public synchronized void getCredit(int time) throws TimeOutException { 
     
        if (credits > 0) { 
            credits--;
            return;
        }
        
        if (time < 0) { 
            time = 0;
        }
        
        long endTime = 0;
        long timeLeft = time;
       
        if (time > 0) { 
            endTime = System.currentTimeMillis() + time;
        }
      
        while (credits == 0) { 
            try { 
                wait(timeLeft);
            } catch (Exception e) {
                // ignore
            }
            
            if (credits == 0 && time > 0) {  
                timeLeft = endTime - System.currentTimeMillis();
            
                if (timeLeft <= 0) { 
                    throw new TimeOutException("Time limit " + time 
                            + " exceeded!");
                }
            }
        }
        
        credits--;
    }

    
    public synchronized void addCredit() {
        
        if (credits == 0) { 
            notifyAll();
        }
        
        credits++;
    }
    
}
