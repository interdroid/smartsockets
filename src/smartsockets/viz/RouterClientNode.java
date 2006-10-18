package smartsockets.viz;

import java.awt.Color;
import java.util.ArrayList;
import java.util.StringTokenizer;

import smartsockets.hub.servicelink.ClientInfo;

import com.touchgraph.graphlayout.Node;

public class RouterClientNode extends ClientNode {

    private ClientInfo client;
    
    private ArrayList mouseOverText = new ArrayList();
    
    public RouterClientNode(ClientInfo info, HubNode hub) { 
        super(info.getClientAddress().toString(), hub);        
    
        String adr = info.getClientAddress().toString();
                
        System.out.println("Adding router " + adr);
                
        setType(Node.TYPE_CIRCLE);        
        setBackColor(Color.decode("#FF7F24"));
        setNodeBorderInactiveColor(Color.decode("#CD661D"));
        setLabel("R");     
        
        update(info);
    }
    
    private String convert(long tp) { 
        
        if (tp < 1024) { 
            return tp + " bit/s";
        }  
          
        tp = tp / 1024; 
        
        if (tp < 1024) { 
            return tp + " Kbit/s";
        }  
        
        tp = tp / 1024;         
        return tp + " Mbit/s";
    }
       
    public void update(ClientInfo info) {
    
        mouseOverText.clear();
        
        String adr = info.getClientAddress().toString();
                        
        mouseOverText.add("Router     : " + adr);    
        
        String stats = info.getProperty("statistics");
        
        if (stats == null || stats.length() == 0) {                       
            mouseOverText.add("Connections: 0");
            mouseOverText.add("Throughput : 0 Mbit/s");
            setMouseOverText((String []) mouseOverText.toArray(new String[0]));
            return;
        }
        
        StringTokenizer t = new StringTokenizer(stats, ", ");
            
        if (t.countTokens() == 0) {
            System.out.println("Got junk in router statistics! " + stats);
            mouseOverText.add("Connections: 0");
            mouseOverText.add("Throughput : 0 Mbit/s");
            setMouseOverText((String []) mouseOverText.toArray(new String[0]));
            return;
        }
            
        int connections = Integer.parseInt(t.nextToken());
    
        mouseOverText.add("Connections: " + connections);
        
        if (connections == 0) {
            mouseOverText.add("Throughput : 0 Mbit/s");
            setMouseOverText((String []) mouseOverText.toArray(new String[0]));
            return;
        }
                
        for (int i=0;i<connections;i++) { 
            String from = t.nextToken();
            String to = t.nextToken();
            
            long tput = Long.parseLong(t.nextToken());
            
            mouseOverText.add("Connection : " + convert(tput) + " " 
                    + from + " <> " + to);
        }
        
        long totalTP = Long.parseLong(t.nextToken());
        
        mouseOverText.add("Throughput : " + convert(totalTP));
        
        setMouseOverText((String []) mouseOverText.toArray(new String[0]));
    }
    
}
