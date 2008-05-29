package ibis.smartsockets.hub.servicelink;

import ibis.smartsockets.direct.DirectSocketAddress;

import java.util.StringTokenizer;

public class HubInfo {

    public final DirectSocketAddress hubAddress;
    public final String name;
    public final long state;
    public final int clients;
    
    public final DirectSocketAddress [] connectedTo;
    public final boolean [] usingSSH;
    
    public HubInfo(String info) { 
        
        if (!info.startsWith("HubInfo(") || !info.endsWith(")")) { 
            throw new IllegalArgumentException("String does not contain " +
                    "HubInfo!"); 
        }

        try { 
            StringTokenizer t = 
                new StringTokenizer(info.substring(8, info.length()-1), ",");

            hubAddress = DirectSocketAddress.getByAddress(t.nextToken());
            name = t.nextToken();
            state = Long.parseLong(t.nextToken());
            clients = Integer.parseInt(t.nextToken());

            int tmp = Integer.parseInt(t.nextToken());

            connectedTo = new DirectSocketAddress[tmp];
            usingSSH = new boolean[tmp];
            
            for (int i=0;i<connectedTo.length;i++) {
                
                String address = t.nextToken();
                
                if (address.endsWith(" (SSH)")) { 
                    address = address.substring(0, address.length()-6);
                    usingSSH[i] = true;
                } else { 
                    usingSSH[i] = false;      
                }
                
                connectedTo[i] = DirectSocketAddress.getByAddress(address);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("String does not contain HubInfo" 
                    + ": \"" + info + "\"", e);
        }            
    }
    
   
    
}


