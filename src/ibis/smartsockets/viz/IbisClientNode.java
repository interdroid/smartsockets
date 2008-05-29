package ibis.smartsockets.viz;

import ibis.smartsockets.hub.servicelink.ClientInfo;

import java.awt.Color;


import com.touchgraph.graphlayout.Node;

public class IbisClientNode extends ClientNode {

    public IbisClientNode(ClientInfo info, HubNode hub) {
        
        super(info.getClientAddress().toString(), hub);         
        setType(Node.TYPE_CIRCLE);
    
        String adr = info.getClientAddress().toString();
            
  //      System.out.println("Adding Ibis " + adr);
        
        String id = info.getProperty("ibis");
        
        setMouseOverText(new String[] { 
                "Ibis: " + id, 
                "Loc : " + adr 
                }
        );
        
        setBackColor(Color.decode("#0080A0"));
        setNodeBorderInactiveColor(Color.decode("#00546A")); 
        setLabel("I");
    }
}
