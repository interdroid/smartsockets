package smartsockets.hub;


import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Dictionary;
import java.util.Iterator;

import org.apache.log4j.Logger;

import smartsockets.direct.DirectSocketFactory;
import smartsockets.direct.SocketAddressSet;
import smartsockets.discovery.Callback;
import smartsockets.discovery.Discovery;
import smartsockets.discovery.Sender;
import smartsockets.hub.connections.Connections;
import smartsockets.hub.connections.HubConnection;
import smartsockets.hub.state.HubDescription;
import smartsockets.hub.state.HubList;
import smartsockets.hub.state.StateCounter;

public class Hub extends Thread {
    
    private static int GOSSIP_SLEEP = 10000;
    
    private static final int DEFAULT_DISCOVERY_PORT = 24545;
    
    protected static Logger logger = 
        ibis.util.GetLogger.getLogger(Hub.class.getName());
               
    private HubList proxies;    
    private Connections connections;
    
    private Acceptor acceptor;
    private Connector connector;
            
    private StateCounter state = new StateCounter();
            
    private Discovery discovery;
    
    public Hub() throws IOException { 
        this(null);
    }
    
    public Hub(SocketAddressSet [] proxyAds) throws IOException { 

        super("GossipProxy");
        
        logger.info("Creating GossipProxy");
                
        DirectSocketFactory factory = DirectSocketFactory.getSocketFactory();
        
        // Create the proxy list
        proxies = new HubList(state);
                
        connections = new Connections();
        
        acceptor = new Acceptor(state, connections, proxies, factory);        
        connector = new Connector(state, connections, proxies, factory);
        
        SocketAddressSet local = acceptor.getLocal();         
        
        connector.setLocal(local);
                
        logger.info("GossipAcceptor listning at " + local);
        
        // Create a description for the local machine. 
        HubDescription localDesc = new HubDescription(local, state, true);        
        localDesc.setReachable();
        localDesc.setCanReachMe();
        
        proxies.addLocalDescription(localDesc);

        addProxies(proxyAds);
        
        logger.info("Starting Gossip connector/acceptor");
                
        acceptor.start();
        connector.start();

        logger.info("Listning for broadcast on LAN");
        
        discovery = new Discovery(DEFAULT_DISCOVERY_PORT, 0, 0);         
        discovery.answeringMachine("Any Proxies?", local.toString());
                        
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
        logger.info("AAP I know the following proxies:\n" + proxies.toString());        
                        
        Iterator itt = proxies.connectedProxiesIterator();
        
        while (itt.hasNext()) { 
            HubDescription d = (HubDescription) itt.next();            
            HubConnection c = d.getConnection();
            
            if (c != null) {
                logger.info("Gossip with " + d.proxyAddressAsString); 
                c.gossip(state.get());
            } else { 
                logger.info("Cannot gossip with " + d.proxyAddressAsString 
                        + ": NO CONNECTION!");
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
            new Hub(proxies);
        } catch (IOException e) {
            logger.warn("Oops: ", e);
        }        
    }
}
