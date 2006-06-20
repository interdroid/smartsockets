package ibis.connect.gossipproxy;

import ibis.connect.virtual.VirtualSocketAddress;
import ibis.connect.virtual.VirtualSocketFactory;

import java.io.IOException;
import java.util.Iterator;

import org.apache.log4j.Logger;

public class GossipProxy extends Thread {
    
    private static int GOSSIP_SLEEP = 5000;
    
    protected static Logger logger = 
        ibis.util.GetLogger.getLogger(GossipProxy.class.getName());
               
    private ProxyList proxies;     
    private ProxyAcceptor proxyAcceptor;
    private ProxyConnector proxyConnector;
    
   public GossipProxy() throws IOException { 
        this(null);
    }
    
    public GossipProxy(VirtualSocketAddress [] proxyAds) throws IOException { 

        logger.info("Creating GossipProxy");
                
        VirtualSocketFactory factory = VirtualSocketFactory.getSocketFactory();
        
        // Create the proxy list
        proxies = new ProxyList();
                
        proxyAcceptor = new ProxyAcceptor(proxies, factory);        
        proxyConnector = new ProxyConnector(proxies, factory);
        
        VirtualSocketAddress local = proxyAcceptor.getLocal();         
        
        proxyConnector.setLocal(local);
                
        logger.info("GossipAcceptor listning at " + local);
        
        // Create a description for the local machine. 
        ProxyDescription localDesc = new ProxyDescription(local, null, 0, 0);        
        localDesc.setReachable(1);
        localDesc.setCanReachMe(1, 1);
        
        proxies.addLocalDescription(localDesc);

        addProxies(proxyAds);
        
        logger.info("Starting Gossip connector/acceptor");
                
        proxyAcceptor.start();
        proxyConnector.start();
        
        start();
    }

    void addProxies(VirtualSocketAddress [] proxyAds) { 
        
        if (proxyAds == null || proxyAds.length == 0) { 
            return;
        }
        
        for (int i=0;i<proxyAds.length;i++) { 
            if (proxyAds[i] != null) { 
                proxies.addProxyDescription(proxyAds[i], 0, null);
            } 
        }
    }
    
    private void gossip() { 
        
        logger.info("Starting gossip round....\n");        
        logger.info("I know the following proxies:\n" + proxies.toString());        
                        
        Iterator itt = proxies.connectedProxiesIterator();
        
        while (itt.hasNext()) { 
            ProxyDescription d = (ProxyDescription) itt.next();            
            ProxyConnection c = d.getConnection();
            
            if (c != null) {               
                c.writeProxies(proxies.size());
            }            
        }               
    }
    
    public void run() { 
        
        while (true) { 
            try { 
                logger.info("Sleeping for " + GOSSIP_SLEEP + " ms.");
                Thread.sleep(GOSSIP_SLEEP);
            } catch (InterruptedException e) {
                // ignore
            }
            
            gossip();
        }        
    }
    
    /*
    public synchronized void addClient(VirtualSocketAddress address) {         
        // TODO: Check if we can actually reach the client directly ? 
        localDescription.addClient(address);                
    }
    */
    
    /*
    void activateConnection(ProxyConnection c) {
        // TODO: Should use threadpool
        new Thread(c).start();
    }    
    */
    
    public static void main(String [] args) { 
        
        VirtualSocketAddress [] proxies = new VirtualSocketAddress[args.length];
            
        for (int i=0;i<args.length;i++) {                
            try { 
                proxies[i] = new VirtualSocketAddress(args[i]);
            } catch (Exception e) {
                logger.warn("Skipping proxy address: " + args[i], e);              
            }
        } 
        
        try {
            new GossipProxy(proxies);
        } catch (IOException e) {
            logger.warn("Oops: ", e);
        }        
    }
}
