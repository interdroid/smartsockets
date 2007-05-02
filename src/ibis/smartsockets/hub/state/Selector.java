package ibis.smartsockets.hub.state;

public abstract class Selector {

    public boolean needLocal() { 
        return false;
    }
    
    public boolean needAll() { 
        return false;
    }
    
    public boolean needConnected() { 
        return false;
    }
    
    public abstract void select(HubDescription description);    
}
