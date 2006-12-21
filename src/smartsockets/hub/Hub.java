package smartsockets.hub;


import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.log4j.Logger;

import smartsockets.Properties;
import smartsockets.direct.DirectSocketFactory;
import smartsockets.direct.SocketAddressSet;
import smartsockets.discovery.Discovery;
import smartsockets.hub.connections.BaseConnection;
import smartsockets.hub.connections.HubConnection;
import smartsockets.hub.connections.VirtualConnections;
import smartsockets.hub.state.ConnectionsSelector;
import smartsockets.hub.state.HubDescription;
import smartsockets.hub.state.HubList;
import smartsockets.hub.state.StateCounter;
import smartsockets.util.NetworkUtils;
import smartsockets.util.TypedProperties;

public class Hub extends Thread {
    
    private static int GOSSIP_SLEEP = 3000;
    
    private static final int DEFAULT_DISCOVERY_PORT = 24545;
    private static final int DEFAULT_ACCEPT_PORT    = 17878;    

    private static final Logger misclogger = 
        ibis.util.GetLogger.getLogger("smartsockets.hub.misc");

    private static final Logger goslogger = 
        ibis.util.GetLogger.getLogger("smartsockets.hub.gossip");

    private static final long STAT_FREQ = 60000;
    
    private final HubList hubs;    
    private final Map<SocketAddressSet, BaseConnection> connections;
    
    private final Acceptor acceptor;
    private final Connector connector;
            
    private final StateCounter state = new StateCounter();
            
    private final Discovery discovery;
        
    private final VirtualConnections virtualConnections;
    
    private long nextStats;
    
    public Hub(SocketAddressSet [] hubAddresses, TypedProperties p) 
        throws IOException { 

        super("Hub");
        
        boolean allowDiscovery = 
            p.booleanProperty(Properties.DISCOVERY_ALLOWED, true);
        
        String [] clusters = 
            p.getStringList(Properties.HUB_CLUSTERS, ",", null);
        
        if (clusters == null || clusters.length == 0) {
            clusters = new String[] { "*" };
        }
        
        boolean allowSSHForHub = p.booleanProperty(
                Properties.HUB_SSH_ALLOWED, true);
        
        if (allowSSHForHub) { 
            p.setProperty(Properties.SSH_IN, "true");
            p.setProperty(Properties.SSH_OUT, "true");
        }
        
        if (misclogger.isInfoEnabled()) { 
            misclogger.info("Creating Hub for clusters: " 
                    + Arrays.deepToString(clusters));
        }
                
        DirectSocketFactory factory = DirectSocketFactory.getSocketFactory(p);
        
        // Create the hub list
        hubs = new HubList(state);
                
        connections = Collections.synchronizedMap(
                new HashMap<SocketAddressSet, BaseConnection>());
        
        virtualConnections = new VirtualConnections();
       
        int port = p.getIntProperty(Properties.HUB_PORT, DEFAULT_ACCEPT_PORT);
        
        // NOTE: These are not started until later. We first need to init the
        // rest of the world!        
        acceptor = new Acceptor(port, state, connections, hubs, virtualConnections, factory);        
        connector = new Connector(state, connections, hubs, virtualConnections, factory);
        
        SocketAddressSet local = acceptor.getLocal();         
        
        connector.setLocal(local);
                
        if (goslogger.isInfoEnabled()) {
            goslogger.info("GossipAcceptor listning at " + local);
        }
        
        String name = p.getProperty(Properties.HUB_SIMPLE_NAME); 
                
        if (name == null || name.length() == 0) { 
            // If the simple name is not set, we try to use the hostname 
            // instead.            
            try { 
                name = NetworkUtils.getHostname();
            }  catch (Exception e) {
                if (misclogger.isInfoEnabled()) {
                    misclogger.info("Failed to find simple name for hub!");
                }
            }
        }        
        
        if (misclogger.isInfoEnabled()) {
            misclogger.info("Hub got name: " + name);
        }
        
        // Create a description for the local machine. 
        HubDescription localDesc = new HubDescription(name, local, state, true);        
        localDesc.setReachable();
        localDesc.setCanReachMe();
        
        hubs.addLocalDescription(localDesc);

        addHubs(hubAddresses);
        
        if (goslogger.isInfoEnabled()) {
            goslogger.info("Starting Gossip connector/acceptor");
        }
                
        acceptor.start();
        connector.start();

        if (misclogger.isInfoEnabled()) {
            misclogger.info("Listning for broadcast on LAN");
        }
                      
        if (allowDiscovery) { 
            String [] suffixes = new String[clusters.length];
        
            // TODO: what does the + do exactly ???
            
            // Check if there is a * in the list of clusters. If so, there is no 
            // point is passing any other values. Note that there may also be a 
            // '+' which means 'any machine -NOT- belonging to a cluster. 
            for (int i=0;i<clusters.length;i++) {             
                if (clusters[i].equals("*") && clusters.length > 0) {
                    suffixes = new String[] { "*" };
                    break;
                } else if (clusters[i].equals("+")) { 
                    suffixes[i] = "+";
                } else { 
                    suffixes[i] = " " + clusters[i];
                }
            }

            int dp = p.getIntProperty(Properties.DISCOVERY_PORT, 
                    DEFAULT_DISCOVERY_PORT); 

            discovery = new Discovery(dp, 0, 0);         
            discovery.answeringMachine("Any Proxies?", suffixes, local.toString());
   
            misclogger.info("Hub will reply to discovery requests from: " + 
                    Arrays.deepToString(suffixes));
            
        } else {  
            discovery = null;
            misclogger.info("Hub will not reply to discovery requests!");
        } 
        
        if (goslogger.isInfoEnabled()) {
            goslogger.info("Start Gossiping!");
        }
        
        nextStats = System.currentTimeMillis() + STAT_FREQ;
        
        start();
    }

    public void addHubs(SocketAddressSet [] hubAddresses) { 
        
        if (hubAddresses == null || hubAddresses.length == 0) { 
            return;
        }
        
        for (int i=0;i<hubAddresses.length;i++) { 
            if (hubAddresses[i] != null) { 
                hubs.add(hubAddresses[i]);
            } 
        }
    }
    
    private void gossip() { 
        
        if (goslogger.isInfoEnabled()) {
            goslogger.info("Starting gossip round (local state = " 
                    + state.get() + ")");        
            goslogger.info("I know the following hubs:\n" + hubs.toString());
        }
            
        ConnectionsSelector selector = new ConnectionsSelector();
        
        hubs.select(selector);
        
        for (HubConnection c : selector.getResult()) {
            
            if (c != null) {
                c.gossip();
            } else { 
                if (goslogger.isDebugEnabled()) {
                    goslogger.debug("Cannot gossip with " + c
                            + ": NO CONNECTION!");
                }
            }
        }                   
    }
    
    public SocketAddressSet getHubAddress() { 
        return acceptor.getLocal();
    }
    
    private void statistics() { 
        
        long now = System.currentTimeMillis();
        
        if (now < nextStats) {
            return;
        }
        
        SocketAddressSet [] cons = connections.keySet().toArray(new SocketAddressSet[0]);
        
        for (SocketAddressSet s : cons) { 
            
            BaseConnection b = connections.get(s);
            
            if (b != null) { 
               b.printStatistics();
            }
        }
        
        
        nextStats = now + STAT_FREQ; 
    }
        
    public void run() { 
        
        while (true) { 
            try { 
                if (goslogger.isInfoEnabled()) {
                    goslogger.info("Sleeping for " + GOSSIP_SLEEP + " ms.");
                }
                Thread.sleep(GOSSIP_SLEEP);
            } catch (InterruptedException e) {
                // ignore
            }
            
            gossip();
            
            statistics();
        }        
    }     
}
