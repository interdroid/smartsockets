package ibis.smartsockets.hub.state;

import java.util.LinkedList;

public class StateSelector extends Selector {

    private LinkedList<HubDescription> result = new LinkedList<HubDescription>();    
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

    public LinkedList<HubDescription> getResult() { 
        return result;
    }          
}
