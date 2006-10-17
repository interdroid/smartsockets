package smartsockets.viz;

import java.awt.Color;
import java.util.HashMap;
import java.util.Iterator;

import smartsockets.direct.SocketAddressSet;
import smartsockets.hub.servicelink.HubInfo;

import com.touchgraph.graphlayout.Edge;
import com.touchgraph.graphlayout.Node;

public class HubNode extends Node {
    
    final SmartsocketsViz parent;
    
    private HubInfo info;

    private HashMap edges = new HashMap();        
    private HashMap clients = new HashMap();

    public HubNode(SmartsocketsViz parent, HubInfo info) {
        super(info.hubAddress.toString(), " H ");        
        
        setType(Node.TYPE_CIRCLE);
        setBackColor(Color.decode("#8B2500"));
        setNodeBorderInactiveColor(Color.decode("#5c1800"));
        setMouseOverText(new String[] { "Hub:", info.hubAddress.toString() });
        
        this.parent = parent;
        this.info = info;
    }
        
    public void updateEdges() {

        HashMap oldEdges = edges;
        edges = new HashMap();

        // Refresh existing edges and add new ones..
        for (int i=0;i<info.connectedTo.length;i++) { 

            SocketAddressSet to = info.connectedTo[i];

            Edge e = (Edge) oldEdges.remove(to);

            if (e == null) { 

                HubNode other = parent.getHubNode(to);

                if (other != null) {
                    // we know the target
                    e = new Edge(this, other);
                    parent.addEdge(e);
                }
            }

            if (e != null) { 
                edges.put(to, e);
            }
        }

        // remove old edges
        if (oldEdges.size() > 0) { 
            Iterator itt = oldEdges.values().iterator();

            while (itt.hasNext()) {
                parent.deleteEdge((Edge) itt.next());
            }
        }
    }

    public void delete() {

        // remove edges
        if (edges.size() > 0) { 
            Iterator itt = edges.values().iterator();

            while (itt.hasNext()) {
                parent.deleteEdge((Edge) itt.next());
            }
        }
        
        // delete clients
        if (clients.size() > 0) { 
            Iterator itt = clients.values().iterator();

            while (itt.hasNext()) {
                parent.deleteNode((ClientNode) itt.next());
            }
        }
        
        parent.deleteNode(this);            
    }

    public void updateInfo(HubInfo info) {
        this.info = info;
    }
}
