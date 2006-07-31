package ibis.connect.proxy;

import ibis.connect.direct.DirectSocketFactory;
import ibis.connect.direct.SocketAddressSet;
import ibis.connect.discovery.Discovery;
import ibis.connect.discovery.Sender;
import ibis.connect.proxy.connections.Connections;
import ibis.connect.proxy.connections.ProxyConnection;
import ibis.connect.proxy.state.ProxyDescription;
import ibis.connect.proxy.state.ProxyList;
import ibis.connect.proxy.state.StateCounter;

import java.io.IOException;
import java.util.Iterator;

import org.apache.log4j.Logger;

public class GossipProxy extends Thread {
    
    private static int GOSSIP_SLEEP = 10000;
    
    protected static Logger logger = 
        ibis.util.GetLogger.getLogger(GossipProxy.class.getName());
               
    private ProxyList proxies;    
    private Connections connections;
    
    private ProxyAcceptor acceptor;
    private ProxyConnector connector;
            
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
        
        acceptor = new ProxyAcceptor(state, connections, proxies, factory);        
        connector = new ProxyConnector(state, connections, proxies, factory);
        
        SocketAddressSet local = acceptor.getLocal();         
        
        connector.setLocal(local);
                
        logger.info("GossipAcceptor listning at " + local);
        
        // Create a description for the local machine. 
        ProxyDescription localDesc = new ProxyDescription(local, state, true);        
        localDesc.setReachable();
        localDesc.setCanReachMe();
        
        proxies.addLocalDescription(localDesc);

        addProxies(proxyAds);
        
        logger.info("Starting Gossip connector/acceptor");
                
        acceptor.start();
        connector.start();

        logger.info("Starting LAN advertisement");
        
        Discovery.advertise(0, 0, "Proxy at: " + local);

        logger.info("Start Gossiping!");
        
        start();
    }

    public void addProxies(SocketAddressSet [] proxyAds) { 
        
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
    
    public SocketAddressSet getProxyAddres() { 
        return acceptor.getLocal();
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
