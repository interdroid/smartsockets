package test.virtual.hub;

import java.io.IOException;
import java.util.Arrays;

import ibis.smartsockets.direct.DirectSocketAddress;
import ibis.smartsockets.virtual.VirtualSocketFactory;

public class SimpleHubTest implements Runnable {

    private VirtualSocketFactory factory;
    private int state = 0;
    private boolean done = false;
    
    public SimpleHubTest(VirtualSocketFactory factory) throws IOException {
       
        this.factory = factory;
    
        factory.getServiceLink().registerProperty("smartsockets.viz", 
                "S^state=" + state++);    
    }    
    
    public synchronized void done() {
        done = true;
    }
    
    public synchronized boolean getDone() {
        return done;
    }
        
    public void run() { 
        
        try { 
            while (!getDone()) { 
                DirectSocketAddress[] hubs = factory.getKnownHubs();

                System.out.println("Known hubs: " + Arrays.deepToString(hubs));

                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    // ignore
                }

                factory.getServiceLink().updateProperty("smartsockets.viz", 
                        "S^state=" + state++);
            }

        } catch (Exception e) {
            System.err.println("Oops: " + e);
            e.printStackTrace(System.err);
        } finally {
            try {
                factory.end();
            } catch (Exception e) {
                // ignore
            }
        }

        System.out.println("Done!");
    }
    
    public static void main(String[] args) {

        try { 
            VirtualSocketFactory factory = 
                VirtualSocketFactory.createSocketFactory(null, true);

            System.out.println("Created socket factory");
        
            new SimpleHubTest(factory).run();
            
        } catch (Exception e) {
            System.err.println("Oops: " + e);
            e.printStackTrace(System.err);
        }
    }
}