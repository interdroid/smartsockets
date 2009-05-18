package test.virtual.hub;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Properties;

import ibis.smartsockets.hub.Hub;
import ibis.smartsockets.util.TypedProperties;
import ibis.smartsockets.virtual.InitializationException;
import ibis.smartsockets.virtual.VirtualSocketFactory;

public class HubTest {

    private static int port = 13456;
    private static int clients = 4;
    private static int hubs = 16;
    
    private static LinkedList<String> otherHubs = new LinkedList<String>();
    
    private void start() throws IOException, InitializationException { 
  
        Hub [] h = new Hub[hubs];
       
        for (int i=0;i<hubs;i++) { 
            TypedProperties p = new TypedProperties();
            p.setProperty("smartsockets.hub.port", "" + (port+i));
            h[i] = new Hub(p); 
            
            String address = h[i].getHubAddress().toString();
            
            System.out.println("Hub running at: " + address);
            otherHubs.addFirst(address);
        }
        
        String [] allHubs = otherHubs.toArray(new String[otherHubs.size()]);
        
        for (int i=0;i<hubs;i++) { 
            h[i].addHubs(allHubs);
        
            for (int c=0;c<clients;c++) { 
            
                Properties p = new Properties();
                p.setProperty("name", "S" + c);
                p.setProperty("smartsockets.hub.addresses", h[i].getHubAddress().toString());
            
                @SuppressWarnings("unused")
                SimpleHubTest t = new SimpleHubTest(
                        VirtualSocketFactory.createSocketFactory(p, true));
            }
        }
    }
    
    public static void main(String [] args) throws IOException, InitializationException { 

        for (int i=0;i<args.length;i++) { 
            if (args[i].equals("-hubs") && i < args.length) { 
                hubs = Integer.parseInt(args[++i]); 
            } else if (args[i].equals("-clients") && i < args.length) {
                clients = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-hub") && i < args.length) {
                otherHubs.add(args[++i]);
            } else if (args[i].equals("-port") && i < args.length) {
                port = Integer.parseInt(args[++i]);
            } else { 
                System.err.println("Unknown option: " + args[i]);
            }
        }
        
        new HubTest().start();
    }    
}


