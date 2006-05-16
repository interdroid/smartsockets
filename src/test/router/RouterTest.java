package test.router;

import java.io.IOException;
import java.net.UnknownHostException;

import ibis.connect.router.simple.Router;
import ibis.connect.router.simple.RouterConnection;
import ibis.connect.virtual.VirtualSocketAddress;

public class RouterTest {
    
    private static int timeout = 60000;
    private static boolean startRouter = false;
    private static boolean startClient = true;
    private static boolean serverSide  = false;
    private static String key = null;    
        
    private static VirtualSocketAddress routerAddress;
            
    private static void parseOptions(String [] args) { 
    
        int index = 0;
        
        while (index < args.length) { 
         
            if (args[index].equals("-startRouter")) { 
                startRouter = true;
            } else if (args[index].equals("-noClient")) { 
                startClient = false;
            } else if (args[index].equals("-server")) { 
                serverSide = true;
            } else if (args[index].equals("-router")) { 
                try {
                    routerAddress = new VirtualSocketAddress(args[++index]);
                } catch (UnknownHostException e) {
                    System.out.println("Failed to parse router address " + e);
                }
            } else if (args[index].equals("-timeout")) { 
                timeout = Integer.parseInt(args[++index]);
            } else if (args[index].equals("-key")) { 
                key = args[++index];
            }              
            
            index++;
        }        
    }
        
    public static void main(String [] args) {
    
        Router r = null;
        
        parseOptions(args);
        
        if (startRouter) { 
            try {
                r = new Router();
                r.start();                
            } catch (IOException e) {
                System.out.println("Failed to start router: " + e);
                e.printStackTrace();
                System.exit(1);
            }
        } 

        if (startRouter && routerAddress == null) { 
            // use local router
            routerAddress = r.getAddress();            
        }
   
        if (startClient && routerAddress == null) {
            System.out.println("Cannot start client without router address!");
            System.exit(1);        
        }
        
        if (startClient) {         
            try { 
                RouterConnection c = RouterConnection.connect(routerAddress);
                
                if (serverSide) {                
                    System.out.println("My KEY is : " + c.key);                
                    c.sendAccept(timeout);                    
                    c.out.writeUTF("Hello world!");
                    c.out.flush();
                    
                    c.close();
                } else { 
                    
                    if (key == null) { 
                        System.out.println("Client-side must have key to connect to!");
                        System.exit(1);
                    }
                    
                    c.sendConnect(key, timeout);
                    
                    String message = c.in.readUTF();
                    
                    System.out.println("Server says: " + message);
                    c.close();
                }            
                
            } catch (Exception e) {
                System.err.println("Oops: " + e);
                e.printStackTrace(System.err);
            }
        } 
    }
}
