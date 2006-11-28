package smartsockets.virtual;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

import smartsockets.Properties;
import smartsockets.direct.SocketAddressSet;
import smartsockets.discovery.Discovery;
import smartsockets.hub.servicelink.ServiceLink;
import smartsockets.util.TypedProperties;
import smartsockets.virtual.modules.ConnectModule;

/**
 * This class implements a 'virtual' socket factory.
 *  
 * @author Jason Maassen
 * @version 1.0 Jan 30, 2006
 * @since 1.0
 * 
 */
public class VirtualSocketFactory {
    
    private static final Map<String, VirtualSocketFactory> factories = 
       Collections.synchronizedMap(new HashMap<String, VirtualSocketFactory>());
        
    protected static Logger logger =         
        ibis.util.GetLogger.getLogger("smartsockets.virtual.misc");
            
    protected static Logger conlogger =         
        ibis.util.GetLogger.getLogger("smartsockets.virtual.connect");
    
    private static final Logger statslogger = 
        ibis.util.GetLogger.getLogger("smartsockets.statistics");
   
    private final ArrayList<ConnectModule> modules = 
        new ArrayList<ConnectModule>();

    private final TypedProperties properties;
    
    private final int DEFAULT_BACKLOG;     
    private final int DEFAULT_TIMEOUT;         
  //  private final int DISCOVERY_PORT;
        
    private final HashMap<Integer, VirtualServerSocket> serverSockets = 
        new HashMap<Integer, VirtualServerSocket>();
    
    private int nextPort = 3000;    
    
    private SocketAddressSet myAddresses;      
    private SocketAddressSet hubAddress;    
    
    private VirtualSocketAddress localVirtualAddress;
    private String localVirtualAddressAsString;
    
    private ServiceLink serviceLink;    
    private VirtualClusters clusters;
    
    private VirtualSocketFactory(TypedProperties p) throws Exception {
                
        if (logger.isInfoEnabled()) {
            logger.info("Creating VirtualSocketFactory");
        }
        
        properties = p;
        
        DEFAULT_BACKLOG = p.getIntProperty(Properties.BACKLOG);
        DEFAULT_TIMEOUT = p.getIntProperty(Properties.TIMEOUT);
       // DISCOVERY_PORT = 
    
        // NOTE: order is VERY important here!
        loadModules();        
        loadClusterDefinitions();                        
        createServiceLink();
        startModules();        
        
        if (modules.size() == 0) { 
            logger.warn("Failed to load any modules!");
            throw new Exception("Failed to load any modules!");
        }       
        
        localVirtualAddress = new VirtualSocketAddress(myAddresses, 0, 
                hubAddress, clusters.localCluster());
        
        localVirtualAddressAsString = localVirtualAddress.toString();
    } 
    
    private void loadClusterDefinitions() {         
        clusters = new VirtualClusters(this, properties, getModules()); 
    }

    private void createServiceLink(){ 
        
        SocketAddressSet address = null;
        
        // Check if the proxy address was passed as a property.
        String tmp = properties.getProperty(Properties.HUB_ADDRESS);
        
        if (tmp != null) {
            try { 
                address = new SocketAddressSet(tmp);
            } catch (Exception e) { 
                logger.warn("Failed to understand proxy address: " + tmp, e);                                
            }           
        }
        
        boolean useDiscovery = 
            properties.booleanProperty(Properties.DISCOVERY_ALLOWED, true);
        
        boolean discoveryPreferred = 
            properties.booleanProperty(Properties.DISCOVERY_PREFERRED, false);
        
        // Check if we can discover the proxy address using UDP multicast. 
        if (useDiscovery && (discoveryPreferred || address == null)) {
            if (logger.isInfoEnabled()) { 
                logger.info("Attempting to discover proxy using UDP multicast...");
            }
                        
            int port = properties.getIntProperty(Properties.DISCOVERY_PORT);
            int time = properties.getIntProperty(Properties.DISCOVERY_TIMEOUT);             
                        
            Discovery d = new Discovery(port, 0, time);
            
            String message = "Any Proxies? "; 
            
            message += clusters.localCluster();
            
            String result = d.broadcastWithReply(message);
            
            if (result != null) { 
                try { 
                    address = new SocketAddressSet(result);
                    if (logger.isInfoEnabled()) {
                        logger.info("Hub found at: " + address.toString());
                    }
                } catch (Exception e) {
                    if (logger.isInfoEnabled()) {
                        logger.info("Got unknown reply to hub discovery!");
                    }
                }
            } else { 
                if (logger.isInfoEnabled()) {                    
                    logger.info("No hubs found.");
                }
            }
        }
            
        // Still no address ? Give up...         
        if (address == null) { 
            // properties not set, so no central hub is available
          //  if (logger.isInfoEnabled()) {
                logger.warn("ServiceLink not created: no hub address available!");
          //  }
            return;
        }  
        
        try { 
            serviceLink = ServiceLink.getServiceLink(address, myAddresses);            
            hubAddress = serviceLink.getAddress();            
        } catch (Exception e) {
            logger.warn("Failed to connect service link to hub!", e);
            return;
        }   
        
        // Check if the users want us to register any properties with the hub.
        String [] props = properties.getStringList(
                "smartsockets.register.property", ",", null);
        
        if (props != null && props.length > 0) {
            try { 
                if (props.length == 1) {             
                    serviceLink.registerProperty(props[0], "");
                } else { 
                    serviceLink.registerProperty(props[0], props[1]);
                }
            } catch (Exception e) {
                
                if (props.length == 1) {             
                    logger.warn("Failed to register user property: " + props[0]); 
                } else { 
                    logger.warn("Failed to register user property: " 
                            + props[0] + "=" + props[1]); 
                }
            }
        }
        
    }
    
    private ConnectModule loadModule(String name) {         
             
        if (logger.isInfoEnabled()) {
            logger.info("Loading module: " + name);
        }
        
        String classname = properties.getProperty(
                Properties.MODULES_PREFIX + name, null);
        
        if (classname == null) { 
            // The class implementing the module is not explicitly defined, so 
            // instead we use an 'educated guess' of the form:
            //
            // smartsockets.virtual.modules.<name>.<Name>
            //            
            StringBuffer tmp = new StringBuffer();            
            tmp.append("smartsockets.virtual.modules.");
            tmp.append(name.toLowerCase());
            tmp.append(".");            
            tmp.append(Character.toUpperCase(name.charAt(0)));
            tmp.append(name.substring(1));            
            classname = tmp.toString();
        }
    
        if (logger.isInfoEnabled()) {
            logger.info("    class name: " + classname);
        }
                
        try { 
            ClassLoader cl = Thread.currentThread().getContextClassLoader();        
            Class c = cl.loadClass(classname);
            
            // Check if the class we loaded is indeed a flavor of ConnectModule            
            if (!ConnectModule.class.isAssignableFrom(c)) {                
                logger.warn("Cannot load module " + classname + " since it is " 
                        + " not a subclass of ConnectModule!");
                return null;
            } 

            return (ConnectModule) c.newInstance();
            
        } catch (Exception e) {
            logger.warn("Failed to load module " + classname, e);
        }

        return null;
    }
    
    private void loadModules() throws Exception {         
        // Get the list of modules that we should load...
        String [] mods = 
            properties.getStringList(Properties.MODULES_DEFINE, ",", null);

        int count = mods.length;
        
        if (mods == null || mods.length == 0) { 
            logger.error("No smartsockets modules defined!");
            return;
        }
        
        String [] skip = 
            properties.getStringList(Properties.MODULES_SKIP, ",", null);

        // Remove all modules that should be skipped. 
        if (skip != null) { 
            for (int s=0;s<skip.length;s++) { 
                for (int m=0;m<mods.length;m++) {
                    if (skip[s].equals(mods[m])) {
                        if (logger.isInfoEnabled()) {
                            logger.info("Skipping module " + mods[m]);
                        }
                        mods[m] = null;
                        count--;
                    }
                }
            }        
        }
        
        String t = "";
        
        for (int i=0;i<mods.length;i++) {
            if (mods[i] != null) {            
                t += mods[i] + " ";
            }
        }
        
        if (logger.isInfoEnabled()) {
            logger.info("Loading " + count + " modules: " + t);
        }
                
        for (int i=0;i<mods.length;i++) {
            
            if (mods[i] != null) {              
                try {                                 
                    ConnectModule m = loadModule(mods[i]);            
                    m.init(this, mods[i], properties, logger);

                    SocketAddressSet tmp = m.getAddresses();

                    if (tmp != null) { 
                        if (myAddresses == null) {
                            myAddresses = tmp;
                        } else { 
                            myAddresses = SocketAddressSet.merge(myAddresses, tmp);
                        }                    
                    }

                    modules.add(m);
                } catch (Exception e) {
                    logger.warn("Failed to load module: " + mods[i], e);
                    mods[i] = null;
                    count--;
                }
            } 
        }
        
        if (logger.isInfoEnabled()) {
            logger.info(count + " modules loaded.");
        }
    }

    protected ConnectModule [] getModules() { 
        return modules.toArray(new ConnectModule[modules.size()]);        
    }
        
    protected ConnectModule [] getModules(String [] names) { 

        ArrayList<ConnectModule> tmp = new ArrayList<ConnectModule>();
        
        for (int i=0;i<names.length;i++) {
        
            boolean found = false;
            
            if (names[i] != null && !names[i].equals("none")) {                
                for (int j=0;j<modules.size();j++) {                     
                    ConnectModule m = modules.get(j); 
                    
                    if (m.getName().equals(names[i])) { 
                        tmp.add(m);
                        found = true;
                        break;
                    }
                }
                
                if (!found) { 
                    logger.warn("Module " + names[i] + " not found!");
                }                
            }
        }
        
        return tmp.toArray(new ConnectModule[tmp.size()]);
    }
    
    private void startModules() { 
        
        ArrayList<ConnectModule> failed = new ArrayList<ConnectModule>(); 
        
        if (serviceLink == null) { 
            // No servicelink, so remove all modules that depend on it....
            for (ConnectModule c : modules) {  
                if (c.requiresServiceLink) {
                    failed.add(c);
                }
            }  
        
            for (ConnectModule c : failed) {
                logger.warn("Module " + c.module 
                        + " removed (no serviceLink)!");  
                modules.remove(c);
            }
            
            failed.clear();
        } 
         
        for (ConnectModule c : modules) { 
            try { 
                c.startModule(serviceLink);
            } catch (Exception e) {
                // Remove all modules that fail to start...
                logger.warn("Module " + c.module 
                        + " did not accept serviceLink!", e);
                failed.add(c);
            }
        }
        
        for (ConnectModule c : failed) {
            logger.warn("Module " + c.module 
                    + " removed (exception during setup)!");  
            modules.remove(c);
        }    
        
        failed.clear();
    }
    
    public VirtualServerSocket getServerSocket(int port) {        
        synchronized (serverSockets) {
            return serverSockets.get(port);
        }        
    }
                     
    public ConnectModule findModule(String name) { 

        for (ConnectModule m : modules) {
            if (m.module.equals(name)) { 
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
        
    private VirtualSocket createClientSocket(ConnectModule m, 
            VirtualSocketAddress target, int timeout, Map properties) 
        throws IOException {
        
        if (m.matchRuntimeRequirements(properties)) {
            if (conlogger.isDebugEnabled()) {
                conlogger.debug("Using module " + m.module + " to set up " +
                        "connection to " + target + " timeout = " + timeout);
            }
            
            long start = System.currentTimeMillis();
                            
            try { 
                                               
                VirtualSocket vs = m.connect(target, timeout, properties);

                long end = System.currentTimeMillis();
                                    
                // TODO: move to ibis ?                    
                if (vs != null) {                     
                    vs.setTcpNoDelay(true);
                    if (conlogger.isInfoEnabled()) {
                        conlogger.info("Sucess: " + m.module 
                                + " connected to " + target + " (time = " + 
                                (end-start) + " ms.)");
                    }
                    
                    m.success(end-start);
                    return vs;
                } 
            
                m.failed(end-start);
                
            } catch (ModuleNotSuitableException e) {
                
                long end = System.currentTimeMillis();
                                    
                // Just print and try the next module...
                if (conlogger.isInfoEnabled()) {
                    conlogger.info("Failed: not suitable (time = " + (end-start) 
                            + " ms.)", e);
                }
                
                m.failed(end-start);
            }            
            // NOTE: other exceptions are forwarded to the user!
        } else { 
            if (conlogger.isInfoEnabled()) {
                conlogger.warn("Failed: module " + m.module + " may not be used to set " +
                        "up connection to " + target);
            }            
        }
        
        m.notAllowed();
        return null;
    }
    
    public VirtualSocket createClientSocket(VirtualSocketAddress target, 
            int timeout, Map prop) throws IOException {

        // TODO: should we still have a properties parameter here ? 
        if (prop == null) {
            prop = properties;
        }

        int notSuitableCount = 0;
        
        if (timeout < 0) { 
            timeout = DEFAULT_TIMEOUT;
        }
        
        ConnectModule [] order = clusters.getOrder(target);
              
        if (timeout == 0 && order.length > 1) { 
            timeout = DEFAULT_TIMEOUT;
        }
                
        // Now try the remaining modules (or all of them if we weren't 
        // using the cache in the first place...)
        for (ConnectModule m : order) { 
            VirtualSocket vs = createClientSocket(m, target, timeout, prop);
            
            if (vs != null) {               
                if (notSuitableCount > 0) {                    
                    // We managed to connect, but not with the first module, so 
                    // we remember this to speed up later connections.
                    clusters.succes(target, m);
                }
                return vs;
            }
            
            notSuitableCount++;
        }        
        
        if (notSuitableCount == order.length) {
            if (logger.isInfoEnabled()) {
                logger.info("No suitable module found to connect to " + target);
            }
            
            // No suitable modules found...
            throw new ConnectException("No suitable module found to connect to "
                    + target);
        } else { 
            // Apparently, some modules where suitable but failed to connect. 
            // This is treated as a timeout
            if (logger.isInfoEnabled()) {
                logger.info("None of the modules could to connect to " + target);
            }
                        
            // TODO: is this right ?
            throw new SocketTimeoutException("Timeout during connect to " 
                    + target);
        }        
    } 
        
    private int getPort() {
        
        // TODO: should this be random ? 
        synchronized (serverSockets) {            
            while (true) {                                         
                if (!serverSockets.containsKey(nextPort)) { 
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
                
        synchronized (serverSockets) {
            if (serverSockets.containsKey(port)) { 
                throw new BindException("Port " + port + " already in use!");
            }
            
            VirtualSocketAddress a = 
                new VirtualSocketAddress(myAddresses, port, hubAddress, 
                        clusters.localCluster());
            
            VirtualServerSocket vss = new VirtualServerSocket(this, a, port, 
                    backlog, properties);
            
            serverSockets.put(port, vss);            
            return vss;
        } 
    }
  
    public ServiceLink getServiceLink() { 
        return serviceLink;        
    }
    
    public SocketAddressSet getLocalHost() { 
        return myAddresses;
    }
    
    public String getLocalCluster() { 
        return clusters.localCluster();
    }
    
    public SocketAddressSet getLocalProxy() { 
        return hubAddress;        
    }
    
    protected void closed(int port) {        
        synchronized (serverSockets) {
            serverSockets.remove(new Integer(port));           
        }                
    }
    
    public static VirtualSocketFactory getSocketFactory(String name) {        
        return factories.get(name);
    }
    
    public static void registerSocketFactory(String name, 
            VirtualSocketFactory factory) {        
        factories.put(name, factory);        
    }
        
    public static VirtualSocketFactory createSocketFactory() {
        
        VirtualSocketFactory f = getSocketFactory("default");
        
        if (f == null) { 
            TypedProperties p = Properties.getDefaultProperties();
            p.put("smartsockets.factory.name", "default");            
            f = createSocketFactory(p, false);
        }
        
        return f;        
    }
           
    public static VirtualSocketFactory createSocketFactory(HashMap p, 
            boolean addDefaults) {
        return createSocketFactory(new TypedProperties(p), addDefaults);        
    } 
    
    public static VirtualSocketFactory createSocketFactory(TypedProperties p, 
            boolean addDefaults) {
        
        //logger.warn("Creating VirtualSocketFactory(Prop, bool)!", new Exception());

       // System.err.println("Creating VirtualSocketFactory(Prop, bool)!");
        //new Exception().printStackTrace(System.err);

        
        if (p == null) { 
            p = Properties.getDefaultProperties();            
        } else if (addDefaults) { 
            p.putAll(Properties.getDefaultProperties());
        } 
        
        VirtualSocketFactory factory = null;
        
        String name = p.getProperty("smartsockets.factory.name");

        if (name != null) { 
            factory = factories.get(name);
        }
        
        if (factory == null) { 
            try { 
                factory = new VirtualSocketFactory(p);
                
                if (name != null) {                 
                    factories.put(name, factory);
                }
            } catch (Exception e) {
                logger.warn("Failed to create VirtualSocketFactory!", e);
            }                       
        }
        
        return factory;
    }

    public VirtualSocket createBrokeredSocket(InputStream brokered_in, OutputStream brokered_out, boolean b, Map p) {
        throw new RuntimeException("createBrokeredSocket not implemented");
    }

    public VirtualSocketAddress getLocalVirtual() {
        return localVirtualAddress;
    }
    
    public String getVirtualAddressAsString() {
        return localVirtualAddressAsString;
    }   
    
    public void printStatistics() { 
     
        if (statslogger.isInfoEnabled()) { 
            statslogger.info("======= VirtualSocketFactory ======");
            statslogger.info("");
            statslogger.info("Modules: " + modules.size());
            
            for (ConnectModule c : modules) { 
                statslogger.info("  -- module: " + c.module);
                statslogger.info("  -- succes: " + c.succesfullConnects);
                statslogger.info("  --   time: " + c.connectTime);
                statslogger.info("  -- failed: " + c.failedConnects);
                statslogger.info("  --   time: " + c.failedTime);
                statslogger.info("  --skipped: " + c.failedConnects); 
            }
            
            statslogger.info("");
            statslogger.info("Details:");  
            statslogger.info("");  
             
            for (ConnectModule c : modules) { 
                c.printStatistics();
                statslogger.info("");
            }
            
            if (serviceLink != null) {
                serviceLink.printStatistics();
                statslogger.info("");
            } else { 
                statslogger.info("No servicelink available");
            }    
            
            statslogger.info("===================================");
        }
        
    }
}
