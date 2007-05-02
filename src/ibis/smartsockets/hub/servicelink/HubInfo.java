package ibis.smartsockets.hub.servicelink;

import ibis.smartsockets.direct.DirectSocketAddress;

import java.util.StringTokenizer;


public class HubInfo {

    public final DirectSocketAddress hubAddress;
    public final String name;
    public final long state;
    public final int clients;
    
    public final DirectSocketAddress [] connectedTo;
    
    public HubInfo(String info) { 
        
        if (!info.startsWith("HubInfo(") || !info.endsWith(")")) { 
            throw new IllegalArgumentException("String does not contain " +
                    "HubInfo!"); 
        }

        try { 
            StringTokenizer t = 
                new StringTokenizer(info.substring(8, info.length()-1), ", ");

            hubAddress = DirectSocketAddress.getByAddress(t.nextToken());
            name = t.nextToken();
            state = Long.parseLong(t.nextToken());
            clients = Integer.parseInt(t.nextToken());

            int tmp = Integer.parseInt(t.nextToken());

            connectedTo = new DirectSocketAddress[tmp];

            for (int i=0;i<connectedTo.length;i++) { 
                connectedTo[i] = DirectSocketAddress.getByAddress(t.nextToken());
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("String does not contain HubInfo" 
                    + ": \"" + info + "\"", e);
        }            
    }
    
   
    
}


