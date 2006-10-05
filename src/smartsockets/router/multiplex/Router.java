package smartsockets.router.multiplex;


import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.HashMap;

import org.apache.log4j.Logger;

import smartsockets.direct.SocketAddressSet;
import smartsockets.proxy.servicelink.Client;
import smartsockets.proxy.servicelink.ServiceLink;
import smartsockets.virtual.VirtualServerSocket;
import smartsockets.virtual.VirtualSocket;
import smartsockets.virtual.VirtualSocketAddress;
import smartsockets.virtual.VirtualSocketFactory;

import ibis.util.ThreadPool;

public class Router extends Thread {

    protected static Logger logger = 
        ibis.util.GetLogger.getLogger(Router.class.getName());
             
    private static int ACCEPT_TIMEOUT = 1000; 
        
    private boolean done = false;

    private VirtualSocketFactory factory; 

    private VirtualServerSocket ssc;
   
    private VirtualSocketAddress local; 
    
    private HashMap properties = new HashMap();
    
    private ServiceLink serviceLink;
    
    public Router() throws IOException {
        this(null);        
    }
    
    public Router(SocketAddressSet proxy) throws IOException {         
        
        properties.put("connect.module.skip", "routed"); 
        
        if (proxy != null) { 
            properties.put("connect.proxy.address", proxy);
        }
                
        logger.debug("Router creating VirtualSocketFactory");
        
        factory = VirtualSocketFactory.getSocketFactory(properties, true);        
        serviceLink = factory.getServiceLink();
        
        if (serviceLink == null) { 
            logger.error("Router creating VirtualSocketFactory");
            throw new IOException("Router failed to get service link!");
        } 
        
        ssc = factory.createServerSocket(0, 50, properties);
        
        local = ssc.getLocalSocketAddress();

        logger.info("Router listening on " + local.toString());                 
        
        boolean register = serviceLink.registerService("router", local.toString());
        
        logger.info("Router registration: " + register);                 
    } 
               
    synchronized SocketAddressSet [] getDirections(SocketAddressSet machine) 
        throws IOException {
        
        return serviceLink.locateClient(machine.toString());
    }
    
    synchronized Client [] findClients(SocketAddressSet proxy, String service) 
        throws IOException {
        
        return serviceLink.clients(proxy, service);
    }
    
    VirtualSocket connect(VirtualSocketAddress target, long timeout) 
        throws IOException {        
        return factory.createClientSocket(target, (int) timeout, properties);
    }
    
    
    private final synchronized boolean getDone() { 
        return done;
    }
    
    public synchronized void done() { 
        done = true;
    }
  
    private void performAccept() throws IOException { 
                
        ssc.setSoTimeout(ACCEPT_TIMEOUT);
        
        while (!getDone()) {
            
            System.err.println("Router waiting for connections....");
            
            try { 
                VirtualSocket vs = ssc.accept();                
                ThreadPool.createNew(new Connection(vs, this), 
                        "RouterConnection");
            } catch (SocketTimeoutException e) {
                // ignore
            } 
        } 
    }
        
    public SocketAddressSet getProxyAddress() {
        try { 
            return serviceLink.getAddress();
        } catch (IOException e) {
            logger.warn("Router failed to retrieve proxy address!", e);
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
