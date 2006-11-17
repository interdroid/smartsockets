package smartsockets.hub.connections;

import java.util.ArrayList;

import org.apache.log4j.Logger;

public class VirtualConnections {

    private static Logger vclogger = 
        ibis.util.GetLogger.getLogger("smartsockets.hub.connections.virtual");
    
    private final ArrayList<VirtualConnection> vcs = 
        new ArrayList<VirtualConnection>();

    private int usedVCs = 0;
    
    public synchronized VirtualConnection newVC() {

        vclogger.warn("newVC()!");
                
        VirtualConnection result = null;
        
        if (usedVCs == vcs.size()) { 
            result = new VirtualConnection(vcs.size());
            vcs.add(result);                      
        } else { 
            // TODO : not very efficient!        
            for (int i=0;i<vcs.size();i++) {            
                if (vcs.get(i) == null) { 
                    result = new VirtualConnection(i);
                    vcs.set(i, result);
                    break;
                }
            }
            
            if (result == null) { 
                // Oh dear. The data structure seems to be inconsistent!!! We  
                // should have found an emtpy spot in the arraylist somewhere...
                // Lets' print a warning and return a new value anyway.        
                vclogger.warn("The VirtualConnections database is " +
                    "INCONSISTENT (newVC())!");
        
                result = new VirtualConnection(vcs.size());
                vcs.add(result);
            }
        }
        
        usedVCs++;
        return result;            
    }

    public synchronized VirtualConnection newVC(boolean even) {

        vclogger.warn("newVC(" + even + ")!");
        
        final int mod = even ? 0 : 1; 
        
        VirtualConnection result = null;
        
        if (usedVCs == vcs.size()) { 
                        
            while (vcs.size() % 2 != mod) { 
                vcs.add(null);
            }
            
            result = new VirtualConnection(vcs.size());
            vcs.add(result);                      
            
        } else {
            
            for (int i=mod;i<vcs.size();i+=2) {            
                if (vcs.get(i) == null) { 
                    result = new VirtualConnection(i);
                    vcs.set(i, result);
                    break;
                }
            }
            
            if (result == null) {
                
                while (vcs.size() % 2 != mod) { 
                    vcs.add(null);
                }

                result = new VirtualConnection(vcs.size());
                vcs.add(result);
            }
        }
        
        usedVCs++;
        return result;            
    }
    
    public synchronized VirtualConnection newVC(int index) {
        
        if (index < vcs.size() && vcs.get(index) != null) { 
            vclogger.error("The VirtualConnections database is " +
                "INCONSISTENT (newVC(" + index + "))!");
            return null;
        }
        
        VirtualConnection result = new VirtualConnection(index);
        
        while (index >= vcs.size()) {
            // Add the required number of 'blanks' to the end of the array...
            // TODO: is this really necessary ? 
            vcs.add(null);
        } 
        
        vcs.set(index, result);            
        usedVCs++;
        
        vclogger.warn("newVC(" + index + ")!");
        
        return result;
    }
   
    public synchronized VirtualConnection getVC(int index) {

        vclogger.warn("getVC(" + index + ")!");
        
        if (index < 0 || index >= vcs.size()) { 
            return null;
        }
        
        return vcs.get(index);
    }

    public synchronized VirtualConnection removeVC(int index) {

        vclogger.warn("getVC(" + index + ")!");
        
        if (index < 0 || index >= vcs.size()) { 
            return null;
        }
        
        VirtualConnection result = vcs.get(index);
        
        if (result != null) {
            vcs.set(index, null);
        }
        
        return result;        
    }
    
    /*
    public synchronized void freeVC(VirtualConnection vc) {
        
        vclogger.warn("freeVC(" + vc.number + ")");
        
        if (vc.number < vcs.size()) {
            vcs.set(vc.number, null);
        } else {             
            vclogger.warn("The VirtualConnections database is " +
                "INCONSISTENT (freeVC)!");                       
        }

        usedVCs--;
    }*/

    
}
