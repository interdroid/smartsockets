package smartsockets.virtual;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.log4j.Logger;

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
    
    private static final HashMap factories = new HashMap();
        
    private final HashMap connectionSetupCache = new HashMap(); 
    
    protected static Logger logger =         
        ibis.util.GetLogger.getLogger("smartsockets.virtual.misc");
            
    private final ArrayList modules = new ArrayList();

    private final TypedProperties properties;
    
    private final int DEFAULT_BACKLOG;     
    private final int DEFAULT_TIMEOUT;         
    private final int DEFAULT_DISCOVERY_PORT;
        
    private HashMap serverSockets = new HashMap();        
    private int nextPort = 3000;    
    
    private SocketAddressSet myAddresses;      
    private SocketAddressSet proxyAddress;    
    private String cluster;
    
    private ServiceLink serviceLink;
    
    private ClusterDefinition defaultOrder;
    private HashMap clusterSpecificOrder;
            
    private class ClusterDefinition {         
        final String name;        
        final ConnectModule [] order;
        
        ClusterDefinition(String name, ConnectModule [] order) {
            this.name = name;
            this.order = order;
        }
    }
    
    private VirtualSocketFactory(TypedProperties p) throws Exception {
                
        logger.info("Creating VirtualSocketFactory");    
        
        properties = p;
        
        DEFAULT_BACKLOG = p.getIntProperty(Properties.BACKLOG);
        DEFAULT_TIMEOUT = p.getIntProperty(Properties.TIMEOUT);
        DEFAULT_DISCOVERY_PORT = p.getIntProperty(Properties.DISCOVERY_PORT);
    
        // NOTE: order is VERY important here!
        loadModules();        
        loadClusterDefinitions();                        
        createServiceLink();
        passServiceLinkToModules();        
        
        if (modules.size() == 0) { 
            logger.warn("Failed to load any modules!");
            throw new Exception("Failed to load any modules!");
        }       
    } 
    
    private void loadClusterDefinitions() { 
        
        cluster = properties.getProperty(Properties.CLUSTER_MEMBER, null);               
        
        String [] clusters = 
            properties.getStringList(Properties.CLUSTER_DEFINE, ",", null);
               
        if (clusters == null && cluster != null) { 
            logger.warn("No virtual cluster definitions found!");
            return;
        }
        
        if (clusters != null && cluster == null) { 
            logger.warn("I am not a member of any of the virtual clusters!");
            return;
        }

        int myCluster = -1;
        
        for (int i=0;i<clusters.length;i++) { 
            if (clusters[i].equals(cluster)) { 
                myCluster = i;
                break;
            }
        }
        
        if (myCluster == -1) { 
            logger.warn("Missing the virtual cluster definition for " + cluster);
            return;            
        }
        
        logger.info("Processing cluster definitions:");
        logger.info("  - my cluster: " + cluster);

        String prefix = Properties.CLUSTER_PREFIX + cluster + ".";
                
        // First get the 'default' connect rule
        String p = prefix + Properties.CLUSTER_DEFAULT;                 
        String [] tmp = properties.getStringList(p, ",", null);
        
        if (tmp != null) {
            // NOTE the two spaces at the end are intentional!
            defaultOrder = new ClusterDefinition("default", getModules(tmp));            
            logger.info("  - default : " + Arrays.deepToString(tmp));
        }

        // Next, get the rule for how we are supposed to connect inside our own 
        // cluster...                
        p = prefix + Properties.CLUSTER_INSIDE;                
        tmp = properties.getStringList(p, ",", null);
        
        if (tmp != null) { 
            addClusterDefinition(cluster, tmp);
        }

        // Finally, extract the connect rules for any of the other clusters...
        for (int i=0;i<clusters.length;i++) {
            if (i != myCluster) { 
                p = prefix + Properties.CLUSTER_PREFERENCE + clusters[i];
                
                tmp = properties.getStringList(p, ",", null);
                
                if (tmp != null) { 
                    addClusterDefinition(clusters[i], tmp);
                }
            }
        }
    }
    
    private void addClusterDefinition(String name, String[] order) {
        
        logger.info("  - to " + name + " : " + Arrays.deepToString(order));
        
        if (clusterSpecificOrder == null) { 
            clusterSpecificOrder = new HashMap();
        }
        
        clusterSpecificOrder.put(name, 
                new ClusterDefinition(name, getModules(order)));        
    }

    private void createServiceLink(){ 
        
        SocketAddressSet address = null;
        
        // Check if the proxy address was passed as a property.
        String tmp = properties.getProperty(Properties.PROXY);
        
        if (tmp != null) {
            try { 
                address = new SocketAddressSet(tmp);
            } catch (Exception e) { 
                logger.warn("Failed to understand proxy address: " + tmp, e);                                
            }           
        }

        // Check if we can discover the proxy address using UDP multicast. 
        if (address == null) {
         
            logger.info("Attempting to discover proxy using UDP multicast...");                
                        
            Discovery d = new Discovery(DEFAULT_DISCOVERY_PORT, 0, 10000);
            
            String message = "Any Proxies? "; 
            
            if (cluster != null) { 
                message += cluster;
            }
            
            String result = d.broadcastWithReply(message);
            
            if (result != null) { 
                try { 
                    address = new SocketAddressSet(result);                    
                    logger.info("Proxy found at: " + address.toString());                                    
                } catch (Exception e) {
                    logger.info("Got unknown reply to proxy discovery!");                
                }
            } else { 
                logger.info("No proxies found.");
            }
        }
            
        // Still no address ? Give up...         
        if (address == null) { 
            // properties not set, so no central hub is available
            logger.info("ServiceLink not created: no proxy address available!");                
            return;
        }  
        
        try { 
            serviceLink = ServiceLink.getServiceLink(address, myAddresses);            
            proxyAddress = serviceLink.getAddress();            
        } catch (Exception e) {
            logger.warn("Failed to connect service link to proxy!", e);
            return;
        }                        
    }
    
    private ConnectModule loadModule(String name) {         
                        
        logger.info("Loading module: " + name);
        
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
    
        logger.info("    class name: " + classname);
                
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
                        logger.info("Skipping module " + mods[m]);                        
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
        
        logger.info("Loading " + count + " modules: " + t);
                
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

        logger.info(count + " modules loaded, determining order...");
        
        String [] order = 
            properties.getStringList(Properties.MODULES_ORDER, ",", mods);
                        
        defaultOrder = new ClusterDefinition("default", getModules(order)); 
    }

    private ConnectModule [] getModules(String [] names) { 

        ArrayList tmp = new ArrayList();
        
        for (int i=0;i<names.length;i++) {
        
            boolean found = false;
            
            if (names[i] != null && !names[i].equals("none")) {                
                for (int j=0;j<modules.size();j++) {                     
                    ConnectModule m = (ConnectModule) modules.get(j); 
                    
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
        
        return (ConnectModule []) tmp.toArray(new ConnectModule[tmp.size()]);
    }
    
    private void passServiceLinkToModules() { 
        
        Iterator itt = modules.iterator();
        
        while (itt.hasNext()) { 
            ConnectModule c = (ConnectModule) itt.next();
            
            try { 
                c.startModule(serviceLink);
            } catch (Exception e) { 
                logger.warn("Module " + c.module 
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
    
    private ConnectModule getModule(String name) { 
        
        // TODO, not very efficient when number of modules is large ...         
        for (int i=0;i<modules.size();i++) {            
            ConnectModule m = (ConnectModule) modules.get(i);
            
            if (m.module.equals(name)) { 
                return m;
            }            
        }
            
        return null;        
    }
    
    private VirtualSocket createClientSocket(ConnectModule m, 
            VirtualSocketAddress target, int timeout, Map properties) 
        throws IOException {
        
        if (m.matchRuntimeRequirements(properties)) {
            
            logger.info("Using module " + m.module + " to set up " +
                    "connection to " + target);
            
            long start = System.currentTimeMillis();
                            
            try { 
                                               
                VirtualSocket vs = m.connect(target, timeout, properties);

                long end = System.currentTimeMillis();
                                    
                // TODO: move to ibis ?                    
                if (vs != null) {                     
                    vs.setTcpNoDelay(true);                    
                    logger.debug("Sucess with module " + m.module 
                            + " connected to " + target + " (time = " + 
                            (end-start) + " ms.)");
                    
                    return vs;
                } 
            } catch (ModuleNotSuitableException e) {
                
                long end = System.currentTimeMillis();
                                    
                // Just print and try the next module...
                logger.info("Module not suitable (time = " + (end-start) 
                        + " ms.)", e);
            }            
            // NOTE: other exceptions are forwarded to the user!
        } else { 
            logger.info("Module " + m.module + " may not be used to set " +
                    "up connection to " + target);
            
        }
        
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
        
        String targetCluster = target.cluster();
        
        ClusterDefinition cd = defaultOrder;
        
        if (targetCluster != null && clusterSpecificOrder != null) {
            if (clusterSpecificOrder.containsKey(targetCluster)) {             
               cd = (ClusterDefinition) clusterSpecificOrder.get(targetCluster);
            }
        }
        
        if (timeout == 0 && cd.order.length > 1) { 
            timeout = DEFAULT_TIMEOUT;
        }
            
        // First check if we remember which module was succesfull the last time
        // we connected to this machine...
        
        /* TODO: repair this...
        boolean cache = false;
        String winner = null;
               
        // Check if the user wants us to use the cache...
        if (prop != null && prop.containsKey("cache.winner")) { 
            cache = true;            
            winner = (String) connectionSetupCache.get(target);
        }

        // Check if we can reuse the same module...
        if (winner != null) {             
            ConnectModule m = getModule(winner);
            
            if (m != null) { 
                VirtualSocket vs = createClientSocket(m, target, timeout, 
                        prop);
            
                if (vs != null) { 
                    return vs;
                }
                
                notSuitableCount++;
            }
        }
        */
        
        // Now try the remaining modules (or all of them if we weren't 
        // using the cache in the first place...) 
        for (int i=0;i<cd.order.length;i++) {
            
            ConnectModule m = cd.order[i];
            
            /* TODO: repair this..
            if (winner != null && m.module.equals(winner)) {
                // we already tried this module 
                break;
            }            
            */
            
            VirtualSocket vs = createClientSocket(m, target, timeout, prop);
            
            if (vs != null) {
                /* TODO: repair this...
                if (cache) { 
                    connectionSetupCache.put(target, m.module);
                }
                */              
                return vs;
            }
            
            notSuitableCount++;
        }        
        
        if (notSuitableCount == cd.order.length) {
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
                new VirtualSocketAddress(myAddresses, port, proxyAddress, 
                        cluster);
            
            VirtualServerSocket vss = new VirtualServerSocket(this, a, port, 
                    backlog, properties);
            
            serverSockets.put(key, vss);            
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
        return cluster;
    }
    
    public SocketAddressSet getLocalProxy() { 
        return proxyAddress;        
    }
    
    protected void closed(int port) {        
        synchronized (serverSockets) {
            serverSockets.remove(new Integer(port));           
        }                
    }
           
    public static VirtualSocketFactory getSocketFactory() {
        return getSocketFactory(Properties.getDefaultProperties(), false);        
    }
    
    public static VirtualSocketFactory getSocketFactory(HashMap p, 
            boolean addDefaults) {    
        return getSocketFactory(new TypedProperties(p), addDefaults);        
    } 
        
    public static VirtualSocketFactory getSocketFactory(TypedProperties p, 
            boolean addDefaults) {
    
        if (p == null) { 
            p = Properties.getDefaultProperties();            
        } else if (addDefaults) { 
            p.putAll(Properties.getDefaultProperties());
        } 
        
        VirtualSocketFactory factory = (VirtualSocketFactory) factories.get(p);
        
        if (factory == null) { 
            try { 
                factory = new VirtualSocketFactory(p);
                factories.put(p, factory);                
            } catch (Exception e) {
                logger.warn("Failed to create VirtualSocketFactory!", e);
            }                       
        }
        
        return factory;
    }

    public VirtualSocket createBrokeredSocket(InputStream brokered_in, OutputStream brokered_out, boolean b, Map p) {
        throw new RuntimeException("createBrokeredSocket not implemented");
    }
}
