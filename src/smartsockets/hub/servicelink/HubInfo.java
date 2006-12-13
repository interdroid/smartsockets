package smartsockets.hub.servicelink;

import java.util.StringTokenizer;

import smartsockets.direct.SocketAddressSet;

public class HubInfo {

    public final SocketAddressSet hubAddress;
    public final String name;
    public final long state;
    public final int clients;
    
    public final SocketAddressSet [] connectedTo;
    
    public HubInfo(String info) { 
        
        if (!info.startsWith("HubInfo(") || !info.endsWith(")")) { 
            throw new IllegalArgumentException("String does not contain " +
                    "HubInfo!"); 
        }

        try { 
            StringTokenizer t = 
                new StringTokenizer(info.substring(8, info.length()-1), ", ");

            hubAddress = SocketAddressSet.getByAddress(t.nextToken());
            name = t.nextToken();
            state = Long.parseLong(t.nextToken());
            clients = Integer.parseInt(t.nextToken());

            int tmp = Integer.parseInt(t.nextToken());

            connectedTo = new SocketAddressSet[tmp];

            for (int i=0;i<connectedTo.length;i++) { 
                connectedTo[i] = SocketAddressSet.getByAddress(t.nextToken());
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("String does not contain HubInfo" 
                    + ": \"" + info + "\"", e);
        }            
    }
    
   
    
}


