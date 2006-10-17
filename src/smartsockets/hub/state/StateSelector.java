package smartsockets.hub.state;

import java.util.Iterator;
import java.util.LinkedList;

public class StateSelector extends Selector {

    private LinkedList result = new LinkedList();    
    private final long state;
    
    public StateSelector(long state) { 
        this.state = state;
    }
    
    public boolean needAll() {
        return true;
    }
    
    public void select(HubDescription description) {       
        if (description.getLastLocalUpdate() > state) {
            result.add(description);
        }
    }

    public LinkedList getResult() { 
        return result;
    }   
    
    public Iterator iterator() { 
        return result.iterator();
    }
    
}
