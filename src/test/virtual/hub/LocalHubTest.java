package test.virtual.hub;

import java.util.HashMap;

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
            
            VirtualServerSocket ss = factory.createServerSocket(5000, 1, null);
            
            System.out.println("Created server socket: " 
                    + ss.getLocalSocketAddress());
            
        } catch (Exception e) {
            System.err.println("Oops: " + e);
            e.printStackTrace(System.err);
        }        
    }
}
