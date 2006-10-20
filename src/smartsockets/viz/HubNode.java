package smartsockets.viz;

import java.awt.Color;
import java.util.HashMap;
import java.util.Iterator;

import smartsockets.direct.SocketAddressSet;
import smartsockets.hub.servicelink.ClientInfo;
import smartsockets.hub.servicelink.HubInfo;

import com.touchgraph.graphlayout.Edge;
import com.touchgraph.graphlayout.Node;

public class HubNode extends Node {
    
    final SmartsocketsViz parent;
    
    private HubInfo info;
    private HashMap edges = new HashMap();        
    private HashMap clients = new HashMap();
    
    private CollectionClientNode clientCollection;
    
    private boolean collapseClients = true;
    
    public HubNode(SmartsocketsViz parent, HubInfo info) {
        super("Hub " + info.hubAddress.toString(), " H ");            
        
        setPopup("CollapsedHubNode");
        
        setType(Node.TYPE_CIRCLE);
        setBackColor(Color.decode("#8B2500"));
        setNodeBorderInactiveColor(Color.decode("#5c1800"));
        setMouseOverText(new String[] { "Hub:", info.hubAddress.toString() });
        
        this.parent = parent;
        this.info = info;
        
        clientCollection = new CollectionClientNode(info.clients, this);
    }
        
    public void deleteEdge(Edge e) { 
        parent.deleteEdge(e);
    }
    
    public void showEdge(Edge e) { 
        parent.addEdge(e);
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
                    e.useArrowHead(true);
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

    public synchronized void updateClients() {
    
        HashMap old = clients;
        clients = new HashMap();
        
        if (info.clients > 0) { 
        
            if (collapseClients) {  
                // fold all clients into 1 node to prevent clutter on the 
                // screen                
                clientCollection.setClients(info.clients);

                CollectionClientNode tmp = 
                    (CollectionClientNode) old.remove("collection");

                if (tmp == null) { 
                    // the collection wasn't shown yet!
                    parent.addNode(clientCollection);
                    parent.addEdge(clientCollection.getEdge());
                }

                clients.put("collection", clientCollection);

            } else { 
                // NOTE: this does not allow clients to 'change shape' during 
                // the run (e.g., a client changing from a router to a normal 
                // client)
                
                ClientInfo [] cs = parent.getClientsForHub(info.hubAddress);
                
                if (cs != null) { 
                    
                    for (int c=0;c<cs.length;c++) {

                        SocketAddressSet a = cs[c].getClientAddress();
                                               
                        ClientNode ci = (ClientNode) old.remove(a);

                        if (ci == null) {
                            
                            if (cs[c].hasProperty("router")) { 
                                ci = new RouterClientNode(cs[c], this);                                
                            } else if (cs[c].hasProperty("visualization")) {
                                ci = new VizClientNode(cs[c], this);                                                            
                            } else { 
                                ci = new NormalClientNode(cs[c], this);                           
                            }
                            
                            parent.addNode(ci);
                            parent.addEdge(ci.getEdge());
                            
                        } else if (ci instanceof RouterClientNode) { 
                            ((RouterClientNode) ci).update(cs[c]);
                        }

                        clients.put(a, ci);
                    }
                }
            }
        } 

        // Now remove all leftover clients...
        if (old.size() > 0) {            
            Iterator itt = old.values().iterator();

            while (itt.hasNext()) {

                ClientNode ci = (ClientNode) itt.next();

                if (ci.getEdge() != null) {
                    parent.deleteEdge(ci.getEdge());
                }

                parent.deleteNode(ci);
            }
        }
    }
    
    public void delete() {

        // remove edges to other hubs
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
                ClientNode n = (ClientNode) itt.next();
                
                if (n.getEdge() != null) {                 
                    parent.deleteEdge(n.getEdge());
                } 
                
                parent.deleteNode(n);
            }
        }
        
        parent.deleteNode(this);            
    }

    public synchronized void updateInfo(HubInfo info) {
        this.info = info;
    }
    
    public synchronized void expandClients() {        
        this.collapseClients = false;
        setPopup("ExpandedHubNode");              
        updateClients();
    }

    public synchronized void collapseClients() {
        this.collapseClients = true;
        setPopup("CollapsedHubNode");
        updateClients();
    }
    
    public synchronized void updateRouters() {
        
        if (collapseClients) { 
            return;
        }
        
        if (clients.size() > 0) { 

            Iterator itt = clients.values().iterator();
            
            while (itt.hasNext()) { 
                ClientNode n = (ClientNode) itt.next();
                
                if (n instanceof RouterClientNode) { 
                    ((RouterClientNode) n).showConnections(clients);
                }
            }
        }
    }    
}
