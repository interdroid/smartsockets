package smartsockets.router.multiplex;


import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Iterator;

import org.apache.log4j.Logger;

import smartsockets.direct.SocketAddressSet;
import smartsockets.hub.servicelink.ClientInfo;
import smartsockets.hub.servicelink.ServiceLink;
import smartsockets.virtual.VirtualServerSocket;
import smartsockets.virtual.VirtualSocket;
import smartsockets.virtual.VirtualSocketAddress;
import smartsockets.virtual.VirtualSocketFactory;

import ibis.util.ThreadPool;

public class Router extends Thread {

    private static final long STATS_UPDATE_TIME = 10000;

    protected static Logger logger = 
        ibis.util.GetLogger.getLogger(Router.class.getName());
             
    private static int ACCEPT_TIMEOUT = 30000; 
        
    private boolean done = false;

    private VirtualSocketFactory factory; 

    private VirtualServerSocket ssc;
   
    private VirtualSocketAddress local; 
    
    private HashMap properties = new HashMap();
    
    private ServiceLink serviceLink;
    
    private long nextConnectionID = 0;

    private HashMap connections = new HashMap();
    
    private String currentStats = "0";
    
    private long time = 0; 
    
    public Router() throws IOException {
        this(null);        
    }
    
    public Router(SocketAddressSet hub) throws IOException {         
        
        properties.put("smartsockets.modules.skip", "routed"); 
        
        if (hub != null) { 
            properties.put("smartsockets.hub", hub.toString());
        }
             
        logger.debug("Router creating VirtualSocketFactory");
        
        factory = VirtualSocketFactory.createSocketFactory(properties, true);        
        serviceLink = factory.getServiceLink();
        
        if (serviceLink == null) { 
            logger.error("Router creating VirtualSocketFactory");
            throw new IOException("Router failed to get service link!");
        } 
        
        ssc = factory.createServerSocket(0, 50, properties);
        
        local = ssc.getLocalSocketAddress();

        logger.info("Router listening on " + local.toString());                 
        
        boolean register = serviceLink.registerProperty("router", local.toString());        
        logger.info("Router registration: " + register);
        
        register = serviceLink.registerProperty("statistics", currentStats);        
        logger.info("Router connections: " + register);               
    } 
               
    synchronized SocketAddressSet [] locateMachine(SocketAddressSet machine) 
        throws IOException {
        
        return serviceLink.locateClient(machine.toString());
    }
    
    synchronized ClientInfo [] findClients(SocketAddressSet hub, String service) 
        throws IOException {
        
        return serviceLink.clients(hub, service);
    }
    
    VirtualSocket connect(VirtualSocketAddress target, long timeout) 
        throws IOException {        
        return factory.createClientSocket(target, (int) timeout, properties);
    }
    
    public SocketAddressSet getLocalAddress() {
        return factory.getLocalHost();
    }
        
    private final synchronized boolean getDone() { 
        return done;
    }
    
    public synchronized void done() { 
        done = true;
    }

    public synchronized void done(String id) {
        connections.remove(id);
    }
    
    public synchronized void add(String id, Connection c) {
        connections.put(id, c);
    }
    
    private synchronized void updateStatistics() {

        String s = "0";
        
        if (connections.size() > 0) { 

            long totalTP = 0;
                        
            StringBuffer result = new StringBuffer("");
            
            result.append(connections.size());

            Iterator itt = connections.values().iterator();
            
            while (itt.hasNext()) { 
                
                Connection c = (Connection) itt.next();
            
                result.append(",");
                result.append(c.from());
                result.append(",");
                result.append(c.to());
                result.append(",");
                result.append(c.linkID());
                result.append(",");
                
                long tp = c.getThroughput();
                totalTP += tp;
                
                result.append(tp);
            }
            
            result.append(",");
            result.append(totalTP);

            s = result.toString();
        }
        
        if (!currentStats.equals(s)) { 

            logger.info("Update router stats to: (" + s + ")");
                        
            try {
                boolean ok = serviceLink.updateProperty("statistics", s);
                
                if (ok) {
                    currentStats = s;
                    return;
                }
                
                logger.warn("Update of router stats refused!");
            } catch (IOException e) {
                logger.warn("Update of router stats failed!", e);
            }
        }        
    }
    
    private String connectionID() { 
        return "C" + nextConnectionID++;       
    }
    
    private void performAccept() throws IOException { 
                
        ssc.setSoTimeout(ACCEPT_TIMEOUT);
        
        while (!getDone()) {
            
            System.err.println("Router waiting for connections....");
            
            try { 
                VirtualSocket vs = ssc.accept();     
                
                ThreadPool.createNew(
                        new Connection(vs, connectionID(), this), 
                        "RouterConnection");
            } catch (SocketTimeoutException e) {
                // ignore
            }
            
            long t = System.currentTimeMillis();
            
            if ((t-time) > STATS_UPDATE_TIME) { 
                time = t;
                updateStatistics();
            }            
        } 
    }
        
    public SocketAddressSet getHubAddress() {
        try { 
            return serviceLink.getAddress();
        } catch (IOException e) {
            logger.warn("Router failed to retrieve hub address!", e);
            return null;
        }
    }
        
    public VirtualSocketAddress getAddress() { 
        return local;
    }
    
    public void run() { 

        try { 
            while (!getDone()) { 
                performAccept();
            }
        } catch (Exception e) {
            System.out.println("oops: " + e);
            // TODO: handle exception
        }
    }    
    
    public static void main(String [] args) {
        
        for (int i=0;i<args.length;i++) { 
            System.err.println("Failed to understand argument: " + args[i]);
            System.exit(1);
        }
        
        try { 
            new Router().start();
        } catch (IOException e) {
            System.err.println("Router throws exception!");
            e.printStackTrace(System.err);
        }
    }


        
}
