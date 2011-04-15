package ibis.smartsockets.viz;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.StringTokenizer;

import ibis.smartsockets.hub.servicelink.ClientInfo;

import com.touchgraph.graphlayout.Edge;
import com.touchgraph.graphlayout.Node;

public class ClientNode extends SmartNode {
    
    private final boolean compact;
    
    protected Edge edge;
    protected HubNode hub;
    
    public ClientNode(String id, HubNode hub, boolean compact) {
        super();
        setRank(1);
        this.compact = compact;
        
        this.hub = hub;
        edge = new Edge(this, hub);
        edge.setLength(20);
        edge.useArrowHead(false);
    }
    
    public ClientNode(ClientInfo info, HubNode hub, boolean compact) {
        super();
        this.hub = hub;
        this.compact = compact;
        setRank(1);
        
        edge = new Edge(this, hub);
        edge.useArrowHead(false);
        edge.setLength(20);

        
        if (compact) {
            setType(Node.TYPE_CIRCLE);
        } else {
            setType(Node.TYPE_ROUNDRECT);
        }
        
        update(info, hub);
    } 
        
    public void update(ClientInfo info, HubNode hub) {
        
        String adr = info.getClientAddress().toString();

     //   System.out.println("Adding client " + adr);

        String tmp = info.getProperty("smartsockets.viz");

        String label    = getElement(tmp, 0, "C");
        String longLabel    = getElement(tmp, 1, "Client");
        String [] popup = getElements(tmp, 2, new String[] { "Client" });
        String color    = getElement(tmp, 3, null);
        String rank     = getElement(tmp, 4, null);
        
        if (compact) {
            if (label.length() > 1) {
                label = label.substring(0, 1);
            }
            setLabel(label);
        } else {
            setLabel(longLabel);
        }
        
        if (color != null && !color.equalsIgnoreCase("invisible")) {
            setPattern(Color.decode(color));
        } else {
            setPattern(hub.getPattern());
        }
        
        if (rank != null) {
            try {
                int r = Integer.parseInt(rank, 10);
                setRank(r);
            } catch(NumberFormatException e) {
                // ignored
            }
        }
                
        ArrayList<String> list = new ArrayList<String>();
        list.addAll(Arrays.asList(popup));
        list.add(adr);
        setMouseOverText(list.toArray(new String[0]));
        
        if (tmp != null && tmp.equalsIgnoreCase("invisible"))  {
            // this.setVisible(false);
        } else if (color != null && color.equalsIgnoreCase("invisible")) {
            // this.setVisible(false);
        } else {
            this.setVisible(true);
        }
    }
    
    private String getElement(String s, int num, String def) {
        
        if (s == null) { 
            return def;
        }
        
        String [] tmp = split(s, "^");
        
        if (tmp.length <= num || tmp[num] == null) {
            return def;
        }

        return tmp[num];
    }
    
    private String [] split(String s, String seperator) { 
        
        StringTokenizer t = new StringTokenizer(s, seperator);
        
        String [] result = new String[t.countTokens()];
        
        for (int i=0;i<result.length;i++) { 
            result[i] = t.nextToken();
        }
        
        return result;
    }
    
    private String [] getElements(String s, int num, String [] def) {
        
        String tmp = getElement(s, num, null);

        if (tmp == null) { 
            return def;
        }
        
        return split(tmp, ",");
    }    
        
    public Edge getEdge() {
        return edge;
    }
}
