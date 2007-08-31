package test.virtual.randomsteal;

import ibis.smartsockets.virtual.VirtualSocketAddress;
import java.io.IOException;

public class Main {
    
    private static int DEFAULT_CLIENT_TIMEOUT = 2000;
    
    private static int DEFAULT_REPEAT = 10;
    private static int DEFAULT_COUNT = 10000;
    
    private static int count = DEFAULT_COUNT;    
    private static int repeat = DEFAULT_REPEAT;
    private static int timeout = DEFAULT_CLIENT_TIMEOUT;
       
    public static void main(String [] args) throws IOException { 
        
        int clients = -1;
      
        VirtualSocketAddress target = null;

        for (int i=0;i<args.length;i++) { 
            if (args[i].equals("-target")) {                                 
                target = new VirtualSocketAddress(args[++i]);
            } else if (args[i].equals("-clients")) {                                 
                clients = Integer.parseInt(args[++i]);                
            } else if (args[i].equals("-repeat")) {                                 
                repeat = Integer.parseInt(args[++i]);  
            } else if (args[i].equals("-count")) {                                 
                count = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-timeout")) {                                 
                timeout = Integer.parseInt(args[++i]);                  
            } else { 
                System.err.println("Unknown option: " + args[i]);                
            }
        }
       
        if (target == null) {
            try { 
                new Server(clients, count, repeat).start();
            } catch (Exception e) {
                System.out.println("Failed to start server");
                e.printStackTrace();
                System.exit(1);
            }
        } else {
            try { 
                new Client(target, timeout).start();
            } catch (Exception e) {
                System.out.println("Failed to start server");
                e.printStackTrace();
                System.exit(1);
            }
        }
    }
}
