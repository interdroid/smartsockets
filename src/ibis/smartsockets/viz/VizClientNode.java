package ibis.smartsockets.viz;

import ibis.smartsockets.hub.servicelink.ClientInfo;

import java.awt.Color;


import com.touchgraph.graphlayout.Node;

public class VizClientNode extends ClientNode {

    VizClientNode(ClientInfo info, HubNode hub) { 
        super(info.getClientAddress().toString(), hub);         
        setType(Node.TYPE_CIRCLE);
        
        String adr = info.getClientAddress().toString();
                
        System.out.println("Adding visualization " + adr);
        setMouseOverText(new String[] { "Visualization:", adr });
        setBackColor(Color.decode("#8000A0"));
        setNodeBorderInactiveColor(Color.decode("#54006A")); 
        setLabel("V");
    }
}
 
