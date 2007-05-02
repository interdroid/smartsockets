package ibis.smartsockets.hub.servicelink;

public class Credits {

    private final int MAX_CREDITS;
    private int credits;
    
    Credits(int max) { 
        MAX_CREDITS = max;
        credits = max;
    }
    
    public void getCredit() { 
    
        try { 
            getCredit(0);
        } catch (Exception e) { 
            // never happens!
        }
    }

    public synchronized void getCredit(int time) throws TimeOutException { 
     
        if (credits > 0) { 
          //  System.err.println("Credits: " + credits);
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
        
        //System.err.println("Credits: " + credits);
    }

    
    public synchronized void addCredit() {
        
        if (credits == 0) { 
            notifyAll();
        }
        
        credits++;
        
       // System.err.println("Credits: " + credits);
        
        // Sanity check 
        if (credits > MAX_CREDITS) { 
            System.err.println("EEK: credits exceeded " + MAX_CREDITS + "!");
        }
    }
    
}
