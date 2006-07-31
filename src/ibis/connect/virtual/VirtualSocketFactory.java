package ibis.connect.virtual;

import ibis.connect.direct.SocketAddressSet;
import ibis.connect.discovery.Callback;
import ibis.connect.discovery.Discovery;
import ibis.connect.proxy.servicelink.ServiceLinkImpl;
import ibis.connect.virtual.modules.ConnectModule;
import ibis.connect.virtual.modules.direct.Direct;
import ibis.connect.virtual.modules.reverse.Reverse;
import ibis.connect.virtual.modules.routed.Routed;
import ibis.connect.virtual.service.ServiceLink;
import ibis.util.TypedProperties;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.log4j.Logger;

/**
 * This class implements a 'virtual' socket factory.
 *  
 * @author Jason Maassen
 * @version 1.0 Jan 30, 2006
 * @since 1.0
 * 
 */
public class VirtualSocketFactory {

    private static final int DEFAULT_BACKLOG = 20;     
    private static final int DEFAULT_TIMEOUT = 1000;     
    
    protected static Logger logger = 
        ibis.util.GetLogger.getLogger(VirtualSocketFactory.class.getName());
          
    private static class DiscoveryReceiver implements Callback {
        
        private SocketAddressSet result;
        private boolean cont = true;
        
        private synchronized SocketAddressSet get(long time) { 
            
            long end = System.currentTimeMillis() + time;
            long left = time;
            
            while (result == null) {
                try { 
                    wait(left);
                } catch (InterruptedException e) {
                    // ignore
                }
                
                left = end - System.currentTimeMillis();
                
                if (left <= 0) { 
                    break;
                }                
            }
            
            cont = false;
            return result;
        }
        
        public synchronized boolean gotMessage(String message) {
                        
            if (message != null && message.startsWith("Proxy at: ")) {
            
                logger.info("Received proxy advert: \"" + message + "\"");            
                
                try {                    
                    String addr = message.substring(10);                  
                    logger.info("Addr: \"" + addr + "\"");                                                        
                    
                    result = new SocketAddressSet(addr);
                    notifyAll();
                    return false;                    
                } catch (UnknownHostException e) {
                    logger.info("Failed to parse advert!", e);
                    return cont;
                }                               
            } else {
                logger.info("Discarding message: \"" + message + "\"");
                return cont;
            }
        }          
    }
        
    protected static VirtualSocketFactory factory; 
        
    private HashMap serverSockets = new HashMap();        
    private int nextPort = 3000;    
    
    private SocketAddressSet myAddresses;
    
    private final ArrayList modules = new ArrayList();
    
    private ServiceLink serviceLink;
    
    private VirtualSocketFactory(Map properties) throws Exception {
                
        logger.info("Creating VirtualSocketFactory");    
        
        loadModules(properties);
        createServiceLink(properties);
        passServiceLinkToModules();        
        
        if (modules.size() == 0) { 
            logger.warn("Failed to load any modules!");
            throw new Exception("Failed to load any modules!");
        }       
    } 
    
    private void createServiceLink(Map properties){ 
        
        SocketAddressSet address = null;
        
        // Check if the poxy address was passed as a parameter.
        if (properties != null) {
            address = 
                (SocketAddressSet) properties.get("connect.proxy.address");           
        }
       
        // Check if the proxy address was passed as a command line property.
        if (address == null) { 
            String host = TypedProperties.stringProperty(Properties.HUB_HOST);
        
            if (host != null) { 
                try { 
                    logger.info("Got addr \"" + host + "\""); 
                    address = new SocketAddressSet(host);
                } catch (Exception e) {
                    logger.warn("Failed to understand proxy address " + host 
                            + "!", e);                    
                }
            }
        } 

        // Check if the proxy address is broadcast using UDP. 
        if (address == null) {
            DiscoveryReceiver r = new DiscoveryReceiver();            
            Discovery.listnen(0, r);                    
            address = r.get(10000);
        }
            
        // Still no address ? Give up...         
        if (address == null) { 
            // properties not set, so no central hub is available
            logger.info("ServiceLink not created: no proxy address available!");                
            return;
        }  
        
        try { 
            serviceLink = ServiceLinkImpl.getServiceLink(address, myAddresses); 
        } catch (Exception e) {
            logger.warn("Failed to connect service link to proxy!", e);
            return;
        }                        
    }
    
    private void loadModules(Map properties) throws Exception { 
        logger.info("Loading modules:");
        
        // TODO: Hardcoded for now. Should implement some configurable 
        // reflection based scheme here ...
        //
        // Problem: All modules together should define my adress, but some 
        // require a service link before they can init, and the serviceLink 
        // requires my address.....        
        
        try { 
            // Direct module
            ConnectModule m = new Direct();
            m.init(this, logger);
          
            // TODO: should be 'add to set' operation here...
            myAddresses = m.getAddresses();                 
            modules.add(m);           
        } catch (Exception e) {
            logger.warn("Failed to load module: Direct", e);
        }   
            
        try {    
            // Reverse module 
            ConnectModule m = new Reverse();
            m.init(this, logger);
          
            // TODO: should be 'add to set' operation here...
            //myAddresses = m.getAddresses(); 
                
            modules.add(m);
        } catch (Exception e) {
            logger.warn("Failed to load module: Reverse", e);
        }       
        
        try {    
            // Reverse module 
            ConnectModule m = new Routed();
            m.init(this, logger);
          
            // TODO: should be 'add to set' operation here...
            //myAddresses = m.getAddresses(); 
                
            modules.add(m);
        } catch (Exception e) {
            logger.warn("Failed to load module: Reverse", e);
        }       
    }
        
    private void passServiceLinkToModules() { 
        
        Iterator itt = modules.iterator();
        
        while (itt.hasNext()) { 
            ConnectModule c = (ConnectModule) itt.next();
            
            try { 
                c.startModule(serviceLink);
            } catch (Exception e) { 
                logger.warn("Module " + c.name 
                        + " did not accept serviceLink!", e);
                itt.remove();
            }
        }        
    }
    
    public VirtualServerSocket getServerSocket(int port) {        
        synchronized (serverSockets) {
            return (VirtualServerSocket) serverSockets.get(new Integer(port));
        }        
    }
                     
    public ConnectModule findModule(String name) { 

        for (int i=0;i<modules.size();i++) { 
            ConnectModule m = (ConnectModule) modules.get(i);
            if (m.name.equals(name)) { 
                return m;
            }
        } 
        
        return null;
    }
    
    public static void close(VirtualSocket s, OutputStream out, InputStream in) { 
        
        try { 
            if (out != null) {
                out.close();
            }
        } catch (Exception e) {
            // ignore
        }
        
        try { 
            if (in != null) { 
                in.close();
            }
        } catch (Exception e) {
            // ignore
        }
        
        try { 
            if (s != null){ 
                s.close(); 
            }
        } catch (Exception e) {
            // ignore
        }        
    }
    
    public VirtualSocket createClientSocket(VirtualSocketAddress target, 
            int timeout, Map properties) throws IOException {
              
        //int refusedCount = 0;
        int notSuitableCount = 0;
        
        if (timeout < 0) { 
            timeout = DEFAULT_TIMEOUT;
        }
        
        if (timeout == 0 && modules.size() > 1) { 
            timeout = DEFAULT_TIMEOUT;
        }
                               
        // try modules here
        for (int i=0;i<modules.size();i++) {
            
            ConnectModule m = (ConnectModule) modules.get(i);
            
            if (m.matchRequirements(properties)) {
                
                logger.info("Using module " + m.name + " to set up " +
                        "connection to " + target);
                
                try { 
                    VirtualSocket vs = m.connect(target, timeout, properties);

                    // TODO: move to ibis ?                    
                    if (vs != null) {                     
                        vs.setTcpNoDelay(true);                    
                        logger.debug("Sucess with module " + m.name 
                                + " connected to " + target);                    
                        return vs;
                    } 
                } catch (ModuleNotSuitableException e) {
                    // Just print and try the next module...
                    logger.info("Module not suitable", e);
                    notSuitableCount++;
                }            
                // NOTE: other exceptions are forwarded to the user!
            } else { 
                logger.info("Module " + m.name + " may not be used to set " +
                        "up connection to " + target);
                
            }
        }        
        
        if (notSuitableCount == modules.size()) {
            logger.info("No suitable module found to connect to " + target);
                    
            // No suitable modules found...
            throw new ConnectException("No suitable module found to connect to "
                    + target);
        } else { 
            // Apparently, some modules where suitable but failed to connect. 
            // This is treated as a timeout            
            logger.info("None of the modules could to connect to " + target);
                        
            // TODO: is this right ?
            throw new SocketTimeoutException("Timeout during connect to " 
                    + target);
        }        
    } 
        
    private int getPort() {
        
        // TODO: should this be random ? 
        synchronized (serverSockets) {            
            while (true) {                                         
                if (!serverSockets.containsKey(new Integer(nextPort))) { 
                    return nextPort++;
                } else { 
                    nextPort++;
                }
            }
        }        
    }
    
    public VirtualServerSocket createServerSocket(int port, int backlog, 
            boolean retry, Map properties) {
        
        VirtualServerSocket result = null;
        
        while (result == null) {         
            try { 
                result = createServerSocket(port, backlog, null);
            } catch (Exception e) {
                // retry
                if (logger.isDebugEnabled()) { 
                    logger.debug("Failed to open serversocket on port " + port
                            + " (will retry): ", e);
                }
            }
        }
        
        return result;
    }
    
    public VirtualServerSocket createServerSocket(int port, int backlog, 
            Map properties) throws IOException {
        
        if (backlog <= 0) { 
            backlog = DEFAULT_BACKLOG;
        }
        
        if (port <= 0) { 
            port = getPort();
        }
        
        if (logger.isInfoEnabled()) { 
            logger.info("Creating VirtualServerSocket(" + port + ", " 
                    + backlog + ", " + properties + ")");
        }
                
        Integer key = new Integer(port);
        
        synchronized (serverSockets) {
            if (serverSockets.containsKey(key)) { 
                throw new BindException("Port " + port + " already in use!");
            }
            
            VirtualSocketAddress a = 
                new VirtualSocketAddress(myAddresses, port);
            
            VirtualServerSocket vss = new VirtualServerSocket(a, port, backlog, 
                    properties);
            
            serverSockets.put(key, vss);            
            return vss;
        } 
    }

    public VirtualSocket createBrokeredSocket(InputStream in, OutputStream out,
            boolean hintIsServer, Map properties) throws IOException {    
        
        // TODO: REMOVE this at some point ?
        throw new RuntimeException("createBrokeredSocket not implemented");
    }

    public ServiceLink getServiceLink() { 
        return serviceLink;        
    }
    
    public SocketAddressSet getLocalHost() { 
        return myAddresses;
    }
        
    protected void closed(int port) {        
        synchronized (serverSockets) {
            serverSockets.remove(new Integer(port));           
        }                
    }
        
    public static VirtualSocketFactory getSocketFactory() {
        return getSocketFactory(null);        
    }
    
    public static VirtualSocketFactory getSocketFactory(Map properties) {
        
        if (factory == null) { 
            try { 
                factory = new VirtualSocketFactory(properties);                
            } catch (Exception e) {
                logger.warn("Failed to create VirtualSocketFactory!", e);
            }                       
        }
        
        return factory;
    }
}
