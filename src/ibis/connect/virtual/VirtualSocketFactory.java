package ibis.connect.virtual;

import ibis.connect.controlhub.ServiceLink;
import ibis.connect.direct.SocketAddressSet;
import ibis.connect.virtual.modules.ConnectModule;
import ibis.connect.virtual.modules.direct.Direct;
import ibis.connect.virtual.modules.reverse.Reverse;
import ibis.util.TypedProperties;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.HashMap;
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

    protected static VirtualSocketFactory factory; 
        
    private HashMap serverSockets = new HashMap();        
    private int nextPort = 3000;    
    
    private SocketAddressSet myAddresses;
    
    private final ArrayList modules = new ArrayList();
    
    private ServiceLink serviceLink;
    
    private VirtualSocketFactory() throws Exception {
                
        logger.info("Creating VirtualSocketFactory");    
        
        loadModules();
        createServiceLink();
        passServiceLinkToModules();        
        
        if (modules.size() == 0) { 
            logger.warn("Failed to load any modules!");
            throw new Exception("Failed to load any modules!");
        }       
    } 
    
    private void createServiceLink(){ 
        String host = TypedProperties.stringProperty(Properties.HUB_HOST);
        
        if (host == null) { 
            // properties not set, so no central hub is available
            logger.info("ServiceLink not created: no hub address available!");                
            return;
        }  
        
        try { 
            SocketAddressSet address = new SocketAddressSet(host);            
            serviceLink = ServiceLink.getServiceLink(address, myAddresses);                 
        } catch (Exception e) {
            logger.warn("ServiceLink: Failed to connect to hub!", e);
            return;
        }                        
    }
    
    private void loadModules() throws Exception { 
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
    }
        
    private void passServiceLinkToModules() { 
        
        for (int i=0;i<modules.size();i++) { 
            ConnectModule c = (ConnectModule) modules.get(i);
            try { 
                c.startModule(serviceLink);
            } catch (Exception e) { 
                logger.warn("Module " + c.name 
                        + " did not accept serviceLink!", e);
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
    
    public VirtualSocket createClientSocket(VirtualSocketAddress target, 
            int timeout, Map properties) throws IOException {
                
        if (timeout == 0 && modules.size() > 1) { 
            timeout = DEFAULT_TIMEOUT;
        }
                               
        // try modules here
        for (int i=0;i<modules.size();i++) {
            
            boolean skip = false;
            
            ConnectModule m = (ConnectModule) modules.get(i);
            
            if (properties != null) {
                String tmp = (String) properties.get(
                        "connect.module." + m.name + ".skip");
                
                skip = (tmp != null && tmp.equalsIgnoreCase("true")); 
            }

            if (!skip) { 
                try { 
                    VirtualSocket vs = m.connect(target, timeout, properties);
                    // TODO: move to ibis ?
                    vs.setTcpNoDelay(true);
                    return vs;
                } catch (ModuleNotSuitableException e) {
                    // Just print and try the next module...
                    logger.info("Module not suitable", e);
                }            
                // NOTE: other exceptions are forwarded to the user!
            } 
        }        
        
        throw new ConnectException("No suitable module found to connect to "
                + target);        
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
    
    public SocketAddressSet getLocalHost() { 
        return myAddresses;
    }
        
    protected void closed(int port) {        
        synchronized (serverSockets) {
            serverSockets.remove(new Integer(port));           
        }                
    }
    
    public static VirtualSocketFactory getSocketFactory() {
        
        if (factory == null) { 
            try { 
                factory = new VirtualSocketFactory();                
            } catch (Exception e) {
                logger.warn("Failed to create VirtualSocketFactory!", e);
            }                       
        }
        
        return factory;
    }
}
