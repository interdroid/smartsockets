package smartsockets.util;


import java.io.IOException;

import smartsockets.Properties;
import smartsockets.direct.SocketAddressSet;
import smartsockets.hub.Hub;
import smartsockets.router.simple.Router;

public class HubStarter {

    private static final int DEFAULT_ACCEPT_PORT = 17878;    

    private static Hub h;
    private static Router r;
        
    public static void main(String [] args) { 
        
        boolean startRouter = true;
        
        SocketAddressSet [] hubs = new SocketAddressSet[args.length];
        int port = DEFAULT_ACCEPT_PORT;

        // Load the default properties. These include the defaults in the code, 
        // the default property file, and any command line '-D' settings.
        TypedProperties p = Properties.getDefaultProperties();
        
        // Properties can be adjusted further using old-fashioned command 
        // line options.  
        for (int i=0;i<args.length;i++) {                
            
            if (args[i].startsWith("-no-router")) {
                startRouter = false;                
            } else if (args[i].equals("-clusters")) { 
                if (i+1 >= args.length) { 
                    System.out.println("-clusters option requires parameter!");
                    System.exit(1);
                }   
                
                String clusters = args[++i];
                
                p.put("smartsockets.hub.clusters", clusters);
                
                // Check if the property is a comma seperated list of strings
                String [] tmp = null;
                
                try {             
                    tmp = p.getStringList("smartsockets.hub.clusters", ",", null);               
                } catch (Exception e) { 
                    // ignore
                }
                
                if (tmp == null) { 
                    System.out.println("-clusters option has incorrect " + 
                            "parameter: " + clusters);
                    System.exit(1);            
                }                  
                
            } else if (args[i].equals("-port")) { 
                if (i+1 >= args.length) { 
                    System.out.println("-port option requires parameter!");
                    System.exit(1);
                }   
                    
                port = Integer.parseInt(args[++i]);
                                
            } else {                
                // Assume it's an address...
                try { 
                    hubs[i] = new SocketAddressSet(args[i]);
                } catch (Exception e) {
                    System.err.println("Skipping hub address: " + args[i]);
                    e.printStackTrace(System.err);
                }
            } 
        }

        p.put("smartsockets.hub.port", Integer.toString(port));
        
        try {            
            System.out.println("Starting hub....");            
            h = new Hub(hubs, p);            
            System.out.println("Hub running on: " + h.getHubAddress());            
        } catch (IOException e) {
            System.err.println("Oops: failed to start hub");
            e.printStackTrace(System.err);
            System.exit(1);
        }   
        
        if (startRouter) { 
            try {         
                System.out.println("Starting router...");            
                r = new Router(h.getHubAddress());
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
