package smartsockets.hub;


import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

import org.apache.log4j.Logger;

import smartsockets.Properties;
import smartsockets.direct.DirectSocketFactory;
import smartsockets.direct.SocketAddressSet;
import smartsockets.discovery.Discovery;
import smartsockets.hub.connections.Connections;
import smartsockets.hub.connections.HubConnection;
import smartsockets.hub.state.ConnectionsSelector;
import smartsockets.hub.state.HubDescription;
import smartsockets.hub.state.HubList;
import smartsockets.hub.state.StateCounter;
import smartsockets.util.NetworkUtils;
import smartsockets.util.TypedProperties;

public class Hub extends Thread {
    
    private static int GOSSIP_SLEEP = 10000;
    
    private static final int DEFAULT_DISCOVERY_PORT = 24545;
    private static final int DEFAULT_ACCEPT_PORT    = 17878;    

    protected static Logger misclogger = 
        ibis.util.GetLogger.getLogger("smartsockets.hub.misc");

    protected static Logger goslogger = 
        ibis.util.GetLogger.getLogger("smartsockets.hub.gossip");
    
    private HubList hubs;    
    private Connections connections;
    
    private Acceptor acceptor;
    private Connector connector;
            
    private StateCounter state = new StateCounter();
            
    private Discovery discovery;
        
    public Hub(SocketAddressSet [] hubAddresses, TypedProperties p) 
        throws IOException { 

        super("Hub");
        
        String [] clusters = 
            p.getStringList(Properties.HUB_CLUSTERS, ",", null);
        
        if (clusters == null || clusters.length == 0) { 
            clusters = new String[] { "*" };
        }
        
        misclogger.info("Creating Hub for clusters: " 
                + Arrays.deepToString(clusters));
                
        DirectSocketFactory factory = DirectSocketFactory.getSocketFactory();
        
        // Create the hub list
        hubs = new HubList(state);
                
        connections = new Connections();
        
        int port = p.getIntProperty(Properties.HUB_PORT, DEFAULT_ACCEPT_PORT);
        
        acceptor = new Acceptor(port, state, connections, hubs, factory);        
        connector = new Connector(state, connections, hubs, factory);
        
        SocketAddressSet local = acceptor.getLocal();         
        
        connector.setLocal(local);
                
        goslogger.info("GossipAcceptor listning at " + local);
        
        String name = p.getProperty(Properties.HUB_SIMPLE_NAME); 
                
        if (name == null || name.length() == 0) { 
            // If the simple name is not set, we try to use the hostname 
            // instead.            
            try { 
                name = NetworkUtils.getHostname();
            }  catch (Exception e) {
                misclogger.info("Failed to find simple name for hub!");
            }
        }        
        
        misclogger.info("Hub got name: " + name);
        
        // Create a description for the local machine. 
        HubDescription localDesc = new HubDescription(name, local, state, true);        
        localDesc.setReachable();
        localDesc.setCanReachMe();
        
        hubs.addLocalDescription(localDesc);

        addHubs(hubAddresses);
        
        goslogger.info("Starting Gossip connector/acceptor");
                
        acceptor.start();
        connector.start();

        misclogger.info("Listning for broadcast on LAN");
                      
        String [] suffixes = new String[clusters.length];
        
        // Check if there is a * in the list of clusters. If so, there is no 
        // point is passing any other values. Note that there may also be a '+'
        // which means 'any machine -NOT- belonging to a cluster. 
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
                        
        goslogger.info("Start Gossiping!");
        
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
        
        goslogger.info("Starting gossip round (local state = " + state.get() + ")");        
        goslogger.info("I know the following hubs:\n" + hubs.toString());        
            
        ConnectionsSelector selector = new ConnectionsSelector();
        
        hubs.select(selector);
        
        Iterator itt = selector.getResult().iterator(); 
        
        while (itt.hasNext()) { 
            HubConnection c = (HubConnection) itt.next();
            
            if (c != null) {
                c.gossip();
            } else { 
                goslogger.debug("Cannot gossip with " + c
                        + ": NO CONNECTION!");
            }
        }                   
    }
    
    public SocketAddressSet getHubAddress() { 
        return acceptor.getLocal();
    }
        
    public void run() { 
        
        while (true) { 
            try { 
                goslogger.info("Sleeping for " + GOSSIP_SLEEP + " ms.");
                Thread.sleep(GOSSIP_SLEEP);
            } catch (InterruptedException e) {
                // ignore
            }
            
            gossip();
        }        
    }
    
    /*
    public static void main(String [] args) { 
        
        SocketAddressSet [] hubs = new SocketAddressSet[args.length];
         
        TypedProperties p = new TypedProperties();
        String clusters = null;
        
        int port = DEFAULT_ACCEPT_PORT;
        
        for (int i=0;i<args.length;i++) {
            
            if (args[i].equals("-clusters")) { 
                if (i+1 >= args.length) { 
                    System.out.println("-clusters option requires parameter!");
                    System.exit(1);
                }   
                
                clusters = args[++i];
                
            } else if (args[i].equals("-port")) { 
                if (i+1 >= args.length) { 
                    System.out.println("-port option requires parameter!");
                    System.exit(1);
                }   
                    
                port = Integer.parseInt(args[++i]);
                
            } else {
                // Assume it's a hub address.
                try { 
                    hubs[i] = new SocketAddressSet(args[i]);
                } catch (Exception e) {
                    misclogger.warn("Skipping hub address: " + args[i], e);              
                }
            }
        } 
        
        p.put("smartsockets.hub.port", Integer.toString(port));
               
        // TODO: use property file ?         
        if (clusters != null) { 
            p.put("smartsockets.hub.clusters", clusters);
            
            // Check if the property is a comma seperated list of strings
            String [] tmp = null;
            
            try {             
                tmp = p.getStringList("smartsockets.hub.clusters", ",", null);               
            } catch (Exception e) { 
                // ignore
            }
            
            if (tmp == null) { 
                System.out.println("-clusters option has incorrect " + 
                        "parameter: " + clusters);
                System.exit(1);            
            }
        } 
                
        try {
            new Hub(hubs, p);
        } catch (IOException e) {
            misclogger.warn("Oops: ", e);
        }        
    }
    */
}
