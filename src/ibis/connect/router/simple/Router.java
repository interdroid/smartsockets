package ibis.connect.router.simple;

import ibis.connect.virtual.VirtualServerSocket;
import ibis.connect.virtual.VirtualSocket;
import ibis.connect.virtual.VirtualSocketAddress;
import ibis.connect.virtual.VirtualSocketFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.util.HashMap;

public class Router extends Thread {

    private static int DEFAULT_PORT = 16543; 
    private static int ACCEPT_TIMEOUT = 1000; 
        
    private final int port;
    private boolean done = false;
    
    private VirtualServerSocket ssc;
    private VirtualSocketFactory factory; 
        
    private HashMap keys = new HashMap();    
    private HashMap properties = new HashMap();
    
    public Router() throws IOException {         
        port = DEFAULT_PORT;        

        properties.put("connect.module.routed.skip", "true"); 
        
        factory = VirtualSocketFactory.getSocketFactory();
        
        ssc = factory.createServerSocket(port, 50, properties);
        
        System.err.println("Created router on: " + ssc);
    } 
        
    protected boolean offerKey(String key, Connection c, long timeout) { 
                
        System.err.println("Router offering key " + key);
        
        long now = System.currentTimeMillis();
        long end = now + timeout;

        synchronized (this) {         
            boolean done = keys.containsKey(key);
        
            while (!done && now < end) { 
        
                try { 
                    wait(end-now);
                } catch (InterruptedException e) { 
                    //  ignore
                }
            
                done = keys.containsKey(key);
                now = System.currentTimeMillis();            
            }

            System.err.println("Router offering key " + key + " result " + done);
        
            if (!done) {
                return false;
            } 
        
            // we found the key, so the accepter is waiting!
            keys.put(key, c);
            notifyAll();
        } 
        
        // Wait for the connection to be created
        c.waitUntilAccepted();
        return true;        
    }

    protected synchronized boolean acceptKey(String key, Connection c, long timeout) { 

        Connection other = null;
        
        System.err.println("Router waiting for " + timeout + " to accept key: " + key);
        
        // Wait for the other to arrive        
        keys.put(key, null);                
        notifyAll(); 
                        
        long now = System.currentTimeMillis();
        long end = now + timeout;
        
        while (other == null && now < end) { 
        
            try { 
                wait(end-now);
            } catch (InterruptedException e) { 
                // ignore
            }
            
            other = (Connection) keys.get(key);
            now = System.currentTimeMillis();            
        }

        keys.remove(key);

        System.err.println("Router wait for key " + key + " resulted in " + other);
                
        if (other != null) { 
            // we have a connection!
            c.createConnection(other);
            return true;
        } else { 
            // no connection 
            return false;
        }
    }

    public static void close(VirtualSocket s, OutputStream out, InputStream in) { 
        
        try {
            in.close();
        } catch (Exception e) {
            // ignore
        }
        
        try { 
            out.close();
        } catch (Exception e) {
            // ignore
        }
        
        try { 
            s.close();
        } catch (Exception e) {
            // ignore
        }
        
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
}
