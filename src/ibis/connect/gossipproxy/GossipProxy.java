package ibis.connect.gossipproxy;

import ibis.connect.direct.DirectSocketFactory;
import ibis.connect.direct.SocketAddressSet;

import java.io.IOException;
import java.util.Iterator;

import org.apache.log4j.Logger;

public class GossipProxy extends Thread {
    
    private static int GOSSIP_SLEEP = 10000;
    
    protected static Logger logger = 
        ibis.util.GetLogger.getLogger(GossipProxy.class.getName());
               
    private ProxyList proxies;    
    private Connections connections;
    
    private ProxyAcceptor proxyAcceptor;
    private ProxyConnector proxyConnector;
            
    private StateCounter state = new StateCounter();
            
    public GossipProxy() throws IOException { 
        this(null);
    }
    
    public GossipProxy(SocketAddressSet [] proxyAds) throws IOException { 

        super("GossipProxy");
        
        logger.info("Creating GossipProxy");
                
        DirectSocketFactory factory = DirectSocketFactory.getSocketFactory();
        
        // Create the proxy list
        proxies = new ProxyList(state);
                
        connections = new Connections();
        
        proxyAcceptor = new ProxyAcceptor(state, connections, proxies, factory);        
        proxyConnector = new ProxyConnector(state, connections, proxies, factory);
        
        SocketAddressSet local = proxyAcceptor.getLocal();         
        
        proxyConnector.setLocal(local);
                
        logger.info("GossipAcceptor listning at " + local);
        
        // Create a description for the local machine. 
        ProxyDescription localDesc = new ProxyDescription(local, state, true);        
        localDesc.setReachable();
        localDesc.setCanReachMe();
        
        proxies.addLocalDescription(localDesc);

        addProxies(proxyAds);
        
        logger.info("Starting Gossip connector/acceptor");
                
        proxyAcceptor.start();
        proxyConnector.start();
        
        start();
    }

    void addProxies(SocketAddressSet [] proxyAds) { 
        
        if (proxyAds == null || proxyAds.length == 0) { 
            return;
        }
        
        for (int i=0;i<proxyAds.length;i++) { 
            if (proxyAds[i] != null) { 
                proxies.add(proxyAds[i]);
            } 
        }
    }
    
    private void gossip() { 
        
        logger.info("Starting gossip round (local state = " + state.get() + ")");        
        logger.info("I know the following proxies:\n" + proxies.toString());        
                        
        Iterator itt = proxies.connectedProxiesIterator();
        
        while (itt.hasNext()) { 
            ProxyDescription d = (ProxyDescription) itt.next();            
            ProxyConnection c = d.getConnection();
            
            if (c != null) {               
                c.gossip(state.get());
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
    
    public static void main(String [] args) { 
        
        SocketAddressSet [] proxies = new SocketAddressSet[args.length];
            
        for (int i=0;i<args.length;i++) {                
            try { 
                proxies[i] = new SocketAddressSet(args[i]);
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
