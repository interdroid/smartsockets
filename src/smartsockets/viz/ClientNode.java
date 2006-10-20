package smartsockets.viz;

import com.touchgraph.graphlayout.Edge;
import com.touchgraph.graphlayout.Node;

public class ClientNode extends Node {

    protected Edge edge;
    protected HubNode hub;
    
    public ClientNode(String id, HubNode hub) { 
        
        super(id);        
        
        this.hub = hub;
        edge = new Edge(this, hub);
        edge.useArrowHead(true);
    }
    
    public Edge getEdge() {
        return edge;
    }
}
