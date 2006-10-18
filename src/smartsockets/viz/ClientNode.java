package smartsockets.viz;

import com.touchgraph.graphlayout.Edge;
import com.touchgraph.graphlayout.Node;

public class ClientNode extends Node {

    private Edge edge;
    private HubNode hub;
    
    public ClientNode(String id, HubNode hub) { 
        
        super(id);        
        
        this.hub = hub;
        edge = new Edge(this, hub);
    }
    
    public Edge getEdge() {
        return edge;
    }        
}
