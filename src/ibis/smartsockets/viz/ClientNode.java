package ibis.smartsockets.viz;

import java.awt.Color;

import ibis.smartsockets.hub.servicelink.ClientInfo;

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
    
    public ClientNode(ClientInfo info, HubNode hub) { 
        
        this(info.getClientAddress().toString(), hub);        
        
        setType(Node.TYPE_CIRCLE);

        String adr = info.getClientAddress().toString();

        System.out.println("Adding client " + adr);

        String label = info.getProperty("smartsockets.viz.label");
        String color = info.getProperty("smartsockets.viz.color");
        String bg = info.getProperty("smartsockets.viz.bgcolor");
        String popup = info.getProperty("smartsockets.viz.popup");
        
        if (label == null) { 
            setLabel("C");
        }        
        
        setLabel(label);
        
        if (color != null) { 
            setBackColor(Color.decode(color));
        }
        
        if (bg != null) {
            setNodeBorderInactiveColor(Color.decode(bg));
        }
        
        if (popup != null) { 
            setMouseOverText(parsePopup(popup));
        } else { 
            setMouseOverText(new String[] { "Client:", adr });
        }
    }
    
    private String [] parsePopup(String s) { 
        return new String[] { "eek", "ook", "aak" };
    }
    
    
    public Edge getEdge() {
        return edge;
    }
}
