package ibis.connect.gossipproxy.router;

import ibis.connect.virtual.VirtualServerSocket;
import ibis.connect.virtual.VirtualSocket;
import ibis.connect.virtual.VirtualSocketAddress;
import ibis.connect.virtual.VirtualSocketFactory;
import ibis.connect.virtual.service.ServiceLink;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.HashMap;

import org.apache.log4j.Logger;

public class Router extends Thread {

    protected static Logger logger = 
        ibis.util.GetLogger.getLogger(Router.class.getName());
             
    private static int ACCEPT_TIMEOUT = 1000; 
        
    private boolean done = false;
    
    private VirtualServerSocket ssc;
    private VirtualSocketFactory factory; 

    private HashMap properties = new HashMap();
    
    private ServiceLink serviceLink;
    
    public Router() throws IOException {         
        
        properties.put("connect.module.skip", "routed"); 
        
        logger.debug("Router creating VirtualSocketFactory");
        
        factory = VirtualSocketFactory.getSocketFactory();        
        serviceLink = factory.getServiceLink();
        
        if (serviceLink == null) { 
            logger.error("Router creating VirtualSocketFactory");
            throw new IOException("Router failed to get service link!");
        } 
        
        ssc = factory.createServerSocket(0, 50, properties);
        
        logger.info("Router listening on " + 
                ssc.getLocalSocketAddress().toString());
        
        System.out.println("Router listening on " + 
                ssc.getLocalSocketAddress().toString());        
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
                new Connection(vs, this).start();
            } catch (SocketTimeoutException e) {
                // ignore
            } 
        } 
    }
    
    public VirtualSocketAddress getAddress() { 
        return ssc.getLocalSocketAddress();
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
