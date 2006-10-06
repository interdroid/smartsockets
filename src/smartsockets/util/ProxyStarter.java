package smartsockets.util;


import java.io.IOException;

import smartsockets.direct.SocketAddressSet;
import smartsockets.hub.Hub;
import smartsockets.router.simple.Router;

public class ProxyStarter {
    
    private static Hub p;
    private static Router r;
        
    public static void main(String [] args) { 
        
        boolean startRouter = true;
        
        SocketAddressSet [] proxies = new SocketAddressSet[args.length];
            
        for (int i=0;i<args.length;i++) {                
            
            if (args[i].startsWith("-no-router")) {
                startRouter = false;                
            } else { 
                try { 
                    proxies[i] = new SocketAddressSet(args[i]);
                } catch (Exception e) {
                    System.err.println("Skipping proxy address: " + args[i]);
                    e.printStackTrace(System.err);
                }
            } 
        }
                
        
        try {            
            System.out.println("Starting proxy....");            
            p = new Hub(proxies);            
            System.out.println("Proxy running on: " + p.getHubAddress());            
        } catch (IOException e) {
            System.err.println("Oops: failed to start proxy");
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
