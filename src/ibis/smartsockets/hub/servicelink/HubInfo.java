package ibis.smartsockets.hub.servicelink;

import ibis.smartsockets.direct.DirectSocketAddress;

public class HubInfo {

    public final DirectSocketAddress hubAddress;
    public final String name;
    public final long state;
    public final int clients;
    
    public final String vizInfo;

    
    public final DirectSocketAddress [] connectedTo;
    public final boolean [] usingSSH;
    
    public HubInfo(String info) { 
        
        if (!info.startsWith("HubInfo(") || !info.endsWith(")")) { 
            throw new IllegalArgumentException("String does not contain " +
                    "HubInfo!"); 
        }

        try {
            
            String[] strings = info.substring(8, info.length()-1).split(",");
            
//            StringTokenizer t = 
//                new StringTokenizer(info.substring(8, info.length()-1), ",");

            hubAddress = DirectSocketAddress.getByAddress(strings[0]);
            name = strings[1];
            vizInfo = strings[2];
            state = Long.parseLong(strings[3]);
            clients = Integer.parseInt(strings[4]);

            int tmp = Integer.parseInt(strings[5]);

            connectedTo = new DirectSocketAddress[tmp];
            usingSSH = new boolean[tmp];
            
            for (int i=0;i<connectedTo.length;i++) {
                
                String address = strings[6 + i];
                
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


