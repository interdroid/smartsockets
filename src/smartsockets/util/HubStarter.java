package smartsockets.util;


import java.io.IOException;

import smartsockets.direct.SocketAddressSet;
import smartsockets.hub.Hub;
import smartsockets.router.simple.Router;

public class HubStarter {
    
    private static Hub p;
    private static Router r;
        
    public static void main(String [] args) { 
        
        boolean startRouter = true;
        
        SocketAddressSet [] hubs = new SocketAddressSet[args.length];
            
        for (int i=0;i<args.length;i++) {                
            
            if (args[i].startsWith("-no-router")) {
                startRouter = false;                
            } else { 
                try { 
                    hubs[i] = new SocketAddressSet(args[i]);
                } catch (Exception e) {
                    System.err.println("Skipping hub address: " + args[i]);
                    e.printStackTrace(System.err);
                }
            } 
        }
                
        
        try {            
            System.out.println("Starting hub....");            
            p = new Hub(hubs);            
            System.out.println("Hub running on: " + p.getHubAddress());            
        } catch (IOException e) {
            System.err.println("Oops: failed to start hub");
            e.printStackTrace(System.err);
            System.exit(1);
        }   
        
        if (startRouter) { 
            try {         
                System.out.println("Starting router...");            
                r = new Router(p.getHubAddress());
                System.out.println("Router running on: " + r.getAddress());
                r.start();                                
            } catch (IOException e) {
                System.err.println("Oops: failed to start router");
                e.printStackTrace(System.err);
                System.exit(1);
            }
        } 
    }
}
