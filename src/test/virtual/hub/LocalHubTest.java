package test.virtual.hub;

import java.util.Arrays;
import java.util.HashMap;

import ibis.smartsockets.direct.DirectSocketAddress;
import ibis.smartsockets.virtual.VirtualServerSocket;
import ibis.smartsockets.SmartSocketsProperties;
import ibis.smartsockets.virtual.VirtualSocketFactory;

public class LocalHubTest {

    public static void main(String[] args) {
        
        try { 
            HashMap<String, String> p = new HashMap<String, String>();
            p.put(SmartSocketsProperties.START_HUB, "true");
            p.put(SmartSocketsProperties.HUB_DELEGATE, "true");
            
            VirtualSocketFactory factory = 
                VirtualSocketFactory.createSocketFactory(p, true);
            
            System.out.println("Created socket factory");
            
            DirectSocketAddress [] hubs = factory.getKnownHubs();
            
            System.out.println("Known hubs: " + Arrays.deepToString(hubs)); 
            
            VirtualServerSocket ss = factory.createServerSocket(5000, 1, null);
            
            System.out.println("Created server socket: " 
                    + ss.getLocalSocketAddress());
            
            hubs = factory.getKnownHubs();
            
            System.out.println("Known hubs: " + Arrays.deepToString(hubs)); 
            
            factory.end();
            
            System.out.println("Done!");
            
        } catch (Exception e) {
            System.err.println("Oops: " + e);
            e.printStackTrace(System.err);
        }        
    }
}
