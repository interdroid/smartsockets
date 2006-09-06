package ibis.connect.util;

import java.net.InetAddress;

import org.apache.log4j.Logger;

import de.javawi.jstun.test.DiscoveryInfo;
import de.javawi.jstun.test.DiscoveryTest;

public class STUN {
    
    private static Logger logger = 
        ibis.util.GetLogger.getLogger(STUN.class.getName());
    
    private static String [] DEFAULT_SERVERS = 
        new String [] { "iphone-stun.freenet.de",
                        "stun.xten.net",
                        "stun.fwdnet.net", 
                        "stun.fwd.org" }; 
    
    private static InetAddress external; 
    
    static class Discovery implements Runnable {
        
        private final InetAddress iaddress;        
        private final String server; 
        
        private boolean done = false;               
        private DiscoveryInfo result; 
        
        public Discovery(InetAddress iaddress, String server) {
            this.iaddress = iaddress;
            this.server = server;            
        }
    
        public synchronized DiscoveryInfo getResult() { 
            
            while (!done) { 
                try { 
                    wait();
                } catch (InterruptedException e) {
                    // ignore
                }
            }
            
            return result;
        }
        
        public void run() {                          
            try {
                logger.info("STUN discovery initiated on: " + iaddress);
                
                System.err.println("STUN discovery initiated on: " + iaddress);
                
                DiscoveryTest test = new DiscoveryTest(iaddress, server, 3478);
                result = test.test();
            } catch (Exception e) {
                logger.warn("STUN discovery on " + iaddress + " failed!", e);
            }

            synchronized (this) {
                done = true;
                notifyAll();
            }
        }            
    }
    
    private static void getExternalAddress(String server) {
    
        logger.info("Trying to determine external address using "
                + "STUN server:" + server);
        
        InetAddress [] addresses = NetworkUtils.getAllHostAddresses();            
        Discovery [] tmp = new Discovery[addresses.length];

        logger.info("Network addresses available: " + addresses.length);
        
        // For each network address (except loopback) we start a thread that
        // tries to find the external address using STUN. 
        for (int i=0;i<addresses.length;i++) {                 
            if (!addresses[i].isLoopbackAddress()) {
                tmp[i] = new Discovery(addresses[i], server);                    
                // TODO: thread pool ?                     
                new Thread(tmp[i], "STUN " + addresses[i]).start();
            }
        }
        
        // Next, we gather the results and try to find an external address.            
        for (int i=0;i<tmp.length;i++) {                 
            if (tmp[i] != null) { 
                DiscoveryInfo info = tmp[i].getResult();
                
                if (info == null) {
                    logger.info("STUN failed for " + addresses[i]);            
                } else {
                    logger.info("STUN result for " + addresses[i] + ":\n" + info);
                
                    // TODO multiple external addresses ??? 
                    
                    if (info.isOpenAccess()) {                        
                        external = addresses[i];
                    } else { 
                        external = info.getPublicIP();
                    }
                }
            }
        }
        
        logger.info("Found external address: " + external + " using server " 
                + server);      
    }
    
    public static InetAddress getExternalAddress(String [] servers) {
        
        if (external != null) { 
            return external;
        }
        
        if (servers == null) { 
            servers = DEFAULT_SERVERS;
        }
        
        for (int i=0;i<servers.length;i++) { 
            getExternalAddress(servers[i]);
            
            if (external != null) {
                break;
            }
        }            
        
        return external;
    }
    
}
