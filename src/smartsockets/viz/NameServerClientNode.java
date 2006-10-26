package smartsockets.viz;

import java.awt.Color;

import smartsockets.hub.servicelink.ClientInfo;

import com.touchgraph.graphlayout.Node;

public class NameServerClientNode extends ClientNode {

    public NameServerClientNode(ClientInfo info, HubNode hub) {
        
        super(info.getClientAddress().toString(), hub);         
        setType(Node.TYPE_CIRCLE);
    
        String adr = info.getClientAddress().toString();
            
        System.out.println("Adding NameServer " + adr);
                
        setMouseOverText(new String[] { "Nameserver:", adr });
        
        setBackColor(Color.decode("#808080"));
        setNodeBorderInactiveColor(Color.decode("#545454")); 
        setLabel("N");
    }
}
