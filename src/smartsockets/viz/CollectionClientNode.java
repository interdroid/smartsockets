package smartsockets.viz;

import com.touchgraph.graphlayout.Edge;
import com.touchgraph.graphlayout.Node;

public class CollectionClientNode extends Node {
    
    private Edge edge;
    private HubNode hub;

    public CollectionClientNode(int clients, HubNode hub) { 
        
        super("ClientCollection" + hub.getID());        
        this.hub = hub;

        System.out.println("Adding client client collection: " + clients);

        setType(Node.TYPE_CIRCLE);        
        setMouseOverText(new String[] { "Clients :", "" + clients});
        setLabel("" + clients);
        
        edge = new Edge(this, hub);
    }

    public void setClients(int clients) {
        setMouseOverText(new String[] { "Clients :", "" + clients});
        setLabel("" + clients);
    }
    
    public Edge getEdge() {
        return edge;
    }
}
