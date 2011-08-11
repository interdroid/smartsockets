package ibis.smartsockets;

import ibis.smartsockets.util.TypedProperties;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is use to store a SmartSockets configuration.
 *   
 * @author Jason Maassen
 * @version 1.0 Nov 22, 2007
 * @since 1.0
 * 
 */
public class SmartSocketsProperties { 
	
	/** Default filename of the SmartSockets properties file. */
    public static final String DEFAULT_FILE = "smartsockets.properties";
    
    /** Prefix of all SmartSockets properties. */
    public static final String PREFIX = "smartsockets.";
   
    /** 
     * The SmartSockets property file to load. ("smartsockets.properties") 
     * @see DEFAULT_FILE 
     */ 
    public static final String FILE = PREFIX + "file";
    
    /** 
     * Determines if a hub be started in the VirtualSocketFactory. (false) 
     * @see ibis.smartsockets.virtual.VirtualSocketFactory 
     */ 
    public static final String START_HUB = PREFIX + "start.hub";  
    
    /** Should SmartSockets print statistics ? (false) */
    public static final String STATISTICS_PRINT = PREFIX + "statistics";
    
    /** Prefix used when printing SmartSockets statistics. */    
    public static final String STATISTICS_PREFIX = PREFIX + "statistics.prefix";
    
    /** Time interval between printing SmartSockets statistics. (0 = only print at end)*/        
    public static final String STATISTICS_INTERVAL = PREFIX + "statistics.interval";
    
    /** 
     * Should the VirtualSocketFactoy collect detailed exceptions when a connection setup fails ? (false) 
     * @see ibis.smartsockets.virtual.VirtualSocketFactory
     */
    public static final String DETAILED_EXCEPTIONS = PREFIX + "detailed.exceptions";
    
    /** 
     * Should the DirectSocketFactory create NIO sockets ? (false)
    * @see ibis.smartsockets.direct.DirectSocketFactory
    */
    public static final String NIO = PREFIX + "nio";

    /** 
     * The port range used by DirectSocketFactory.
     * @see ibis.smartsockets.direct.DirectSocketFactory
     * @see ibis.smartsockets.direct.PortRange
     */
    public static final String PORT_RANGE = PREFIX + "port.range";
    
    /** 
     * The backlog (number of pending incoming connections) used by VirtualSocketFactory. (50)     
     * @see ibis.smartsockets.virtual.VirtualSocketFactory
     */
    public static final String BACKLOG = PREFIX + "backlog";
   
    /** 
     * Timeout used in virtual connection setup in milliseconds.     
     * @see ibis.smartsockets.virtual.VirtualSocketFactory
     */
    public static final String CONNECT_TIMEOUT = PREFIX + "timeout.connect";
    
    /** 
     * Timeout used virtual accept in milliseconds. (60000)     
     * @see ibis.smartsockets.virtual.VirtualSocketFactory
     */
    public static final String ACCEPT_TIMEOUT = PREFIX + "timeout.accept";      
    
    /** Prefix for all SmartSockets "external" properties. */
    public static final String EXTERNAL_PREFIX = PREFIX + "external.";
    
    /** 
     * Should the DirectSocketFactory use uPnP to discover this machines external address (used with NAT) ? (false)
     * @see ibis.smartsockets.direct.DirectSocketFactory
     */
    public static final String UPNP = EXTERNAL_PREFIX + "upnp";
    
    /** 
     * Should the DirectSocketFactory use uPnP to enable port forwarding to this machine (used with NAT) ? (false)     * 
     * @see ibis.smartsockets.direct.DirectSocketFactory
     */
    public static final String UPNP_PORT_FORWARDING = EXTERNAL_PREFIX + "upnp.forwarding";        
    
    /** 
     * Should the DirectSocketFactory use STUN to discover this machines external address (used with NAT) ? (false)
     * @see ibis.smartsockets.direct.DirectSocketFactory
     */
    public static final String STUN = EXTERNAL_PREFIX + "stun";   
    
    /** 
     * List of STUN servers to use. 
     * @see ibis.smartsockets.direct.DirectSocketFactory
     */
    public static final String STUN_SERVERS = EXTERNAL_PREFIX + "stun.servers";
    
    /** 
     * Set the external address manually.
     * @see ibis.smartsockets.direct.DirectSocketFactory
     */
    public static final String EXTERNAL_MANUAL = EXTERNAL_PREFIX + "manual";

    /** Prefix for all SmartSockets "discovery" properties. */
    public static final String DISCOVERY_PREFIX    = PREFIX + "discovery.";
    
    /** 
     * Should the VirtualSocketFactory use UDP-broadcast to discover the hub ? (false)
     * @see ibis.smartsockets.virtual.VirtualSocketFactory
     * @see ibis.smartsockets.hub.Hub
     */    
    public static final String DISCOVERY_ALLOWED   = DISCOVERY_PREFIX + "allowed";
    
    /** 
     * Should the VirtualSocketFactory prefer the result of hub discovery over the command line ? (false)
     * @see ibis.smartsockets.virtual.VirtualSocketFactory
     * @see ibis.smartsockets.hub.Hub
     */    
    public static final String DISCOVERY_PREFERRED = DISCOVERY_PREFIX + "preferred"; 
    
    //public static final String DISCOVERY_LISTED = DISCOVERY_PREFIX + "forcelisted"; 
    
    /** 
     * Port used for UDP-broadcast to discover the hub. (24545)  
     * @see ibis.smartsockets.virtual.VirtualSocketFactory
     * @see ibis.smartsockets.hub.Hub
     */    
    public static final String DISCOVERY_PORT = DISCOVERY_PREFIX + "port";
    
    /** 
     * Timeout for UDP-broadcast of discover the hub (in milliseconds). (5000)  
     * @see ibis.smartsockets.virtual.VirtualSocketFactory
     * @see ibis.smartsockets.hub.Hub
     */    
    public static final String DISCOVERY_TIMEOUT = DISCOVERY_PREFIX + "timeout";
    
    /** Prefix for all SmartSockets "hub" properties. */   
    public static final String HUB_PREFIX = PREFIX + "hub.";
    
    /** 
     * Comma separated list of SmartSockets hub addresses. 
     * @see ibis.smartsockets.virtual.VirtualSocketFactory
     * @see ibis.smartsockets.hub.Hub
     */
    public static final String HUB_ADDRESSES = HUB_PREFIX + "addresses";
    
    /** 
     * Name for the hub. Used in visualization. (hostname) 
     * @see ibis.smartsockets.hub.Hub
     */
    public static final String HUB_NAME = HUB_PREFIX + "name";

    /** 
     * Color for the hub. Used in visualization.  
     * @see ibis.smartsockets.virtual.VirtualSocketFactory
     * @see ibis.smartsockets.hub.Hub
     */
    public static final String HUB_VIZ_INFO = HUB_PREFIX + "viz.info";
    
    /** 
     * Cluster names for which the hubs should reply to discovery requests.   
     * @see ibis.smartsockets.hub.Hub
     */
    public static final String HUB_CLUSTERS = HUB_PREFIX + "clusters";
    
    /** 
     * Port used for the hub. (17878)
     * @see ibis.smartsockets.hub.Hub
     */
    public static final String HUB_PORT = HUB_PREFIX + "port";
    
    /** 
     * Can hub use SSH tunnels for connection setup ? (true)
     * @see ibis.smartsockets.hub.Hub
     */
    public static final String HUB_SSH_ALLOWED = HUB_PREFIX + "ssh";
    
    /** 
     * Send buffer size for servicelink used between the hub and its clients. 
     * @see ibis.smartsockets.hub.Hub
     */
    public static final String HUB_SEND_BUFFER = HUB_PREFIX + "sendbuffer";
    
    /** 
     * Receive buffer size for servicelink used between the hub and its clients. 
     * @see ibis.smartsockets.hub.Hub
     */    
    public static final String HUB_RECEIVE_BUFFER = HUB_PREFIX + "receivebuffer";
    
    /** 
     * Should the hub gather statistics. (false)
     * @see ibis.smartsockets.hub.Hub
     */        
    public static final String HUB_STATISTICS = HUB_PREFIX + "statistics";
    
    /** 
     * Interval at which the hub should print statistics (in milliseconds). (60000)
     * @see ibis.smartsockets.hub.Hub
     */        
    public static final String HUB_STATS_INTERVAL = HUB_PREFIX + "statistics.interval";  
    
    /** 
     * Should the hub delegate connection accepts ? (false)
     * @see ibis.smartsockets.hub.Hub
     */        
    public static final String HUB_DELEGATE = HUB_PREFIX + "delegate";
    
    /** 
     * Address used for hub delegation.
     * @see ibis.smartsockets.hub.Hub
     */        
    public static final String HUB_DELEGATE_ADDRESS = HUB_PREFIX + "delegate.address";
    
    /** 
     * Virtual port used for hub delegation. (42)
     * @see ibis.smartsockets.hub.Hub
     */        
    public static final String HUB_VIRTUAL_PORT = HUB_PREFIX + "virtualPort";
    
    /** 
     * File containing hub addresses.
     * @see ibis.smartsockets.hub.Hub
     */        
    public static final String HUB_ADDRESS_FILE = HUB_PREFIX + "addressfile";
        
    /** Prefix for all SmartSockets "sl" (service link) properties. */   
    public static final String SL_PREFIX         = PREFIX + "servicelink.";
    
    /** 
     * Send buffer size for servicelink used between the hub and its clients. 
     * @see ibis.smartsockets.hub.servicelink.ServiceLink
     */
    public static final String SL_SEND_BUFFER    = SL_PREFIX + "sendbuffer";
    
    /** 
     * Receive buffer size for servicelink used between the hub and its clients. 
     * @see ibis.smartsockets.hub.servicelink.ServiceLink
     */
    public static final String SL_RECEIVE_BUFFER = SL_PREFIX + "receivebuffer";
    
    /** 
     * Is a servicelink required ? (false) 
     * @see ibis.smartsockets.hub.servicelink.ServiceLink
     */
    public static final String SL_FORCE = SL_PREFIX + "force";
    
    /** 
     * Timeout for creating a servicelink (in milliseconds). (10000) 
     * @see ibis.smartsockets.hub.servicelink.ServiceLink
     */
    public static final String SL_TIMEOUT = SL_PREFIX + "timeout";
    
    /** 
     * Maximum number of retries when creating a servicelink. (6) 
     * @see ibis.smartsockets.hub.servicelink.ServiceLink
     * @see ibis.smartsockets.virtual.VirtualSocketFactory
     */
    public static final String SL_RETRIES = SL_PREFIX + "retries";

    /** Prefix for all SmartSockets "viz" (visualization) properties. */   
    public static final String VIZ_PREFIX           = PREFIX + "viz.";
    
    /** Text color for SmartSockets visualization. */   
    public static final String VIZ_TEXT_COLOR       = VIZ_PREFIX + "text.color" ;
    
    /** Background color for SmartSockets visualization. */   
    public static final String VIZ_BACKGROUND_COLOR = VIZ_PREFIX + "background.color" ;    
    
    /**  */   
    public static final String MODULES_PREFIX = PREFIX + "modules.";
    public static final String MODULES_DEFINE = MODULES_PREFIX + "define";
    public static final String MODULES_ORDER  = MODULES_PREFIX + "order";
    public static final String MODULES_SKIP   = MODULES_PREFIX + "skip";
    
    public static final String DIRECT_PREFIX         = MODULES_PREFIX + "direct.";  
    public static final String DIRECT_BACKLOG        = DIRECT_PREFIX + "backlog";  
    public static final String DIRECT_TIMEOUT        = DIRECT_PREFIX + "timeout"; 
    public static final String DIRECT_LOCAL_TIMEOUT  = DIRECT_PREFIX + "timeout.local";      
    public static final String DIRECT_SEND_BUFFER    = DIRECT_PREFIX + "sendbuffer";
    public static final String DIRECT_RECEIVE_BUFFER = DIRECT_PREFIX + "receivebuffer";
    public static final String DIRECT_COUNT          = DIRECT_PREFIX + "count";
    public static final String DIRECT_CACHE_IP       = DIRECT_PREFIX + "cacheIP";  

    
    public static final String SSH_PREFIX      = DIRECT_PREFIX + "ssh.";
    public static final String SSH_IN          = SSH_PREFIX + "in";
    public static final String SSH_OUT         = SSH_PREFIX + "out";
    public static final String FORCE_SSH_OUT   = SSH_PREFIX + "out.force";    
    public static final String SSH_PRIVATE_KEY = SSH_PREFIX + "privatekey";
    public static final String SSH_PASSPHRASE  = SSH_PREFIX + "passphrase"; 
    
    public static final String REVERSE_PREFIX       = MODULES_PREFIX + "reverse.";
    public static final String REVERSE_CONNECT_SELF = REVERSE_PREFIX + "selfconnect";
    
    public static final String ROUTED_PREFIX    = MODULES_PREFIX + "hubrouted.";
    public static final String ROUTED_BUFFER    = ROUTED_PREFIX + "size.buffer";
    public static final String ROUTED_FRAGMENT  = ROUTED_PREFIX + "size.fragment";
    public static final String ROUTED_MIN_ACK   = ROUTED_PREFIX + "size.ack";

    
    public static final String NETWORKS_PREFIX  = PREFIX + "networks.";        
    
    public static final String NETWORKS_DEFAULT = NETWORKS_PREFIX + "default";
    public static final String NETWORKS_DEFINE  = NETWORKS_PREFIX + "define";
    public static final String NETWORKS_MEMBER  = NETWORKS_PREFIX + "name";
    
    public static final String NW_RANGE              = "range";    
    public static final String NW_PREFERENCE_PREFIX  = "preference.";    
    public static final String NW_PREFERENCE_INSIDE  = NW_PREFERENCE_PREFIX + "internal";
    public static final String NW_PREFERENCE_DEFAULT = NW_PREFERENCE_PREFIX + "default";

    public static final String NW_FIREWALL_PREFIX    = "firewall.";
    public static final String NW_FIREWALL_ACCEPT    = NW_FIREWALL_PREFIX + "accept";
    public static final String NW_FIREWALL_DENY      = NW_FIREWALL_PREFIX + "deny";
    public static final String NW_FIREWALL_DEFAULT   = NW_FIREWALL_PREFIX + "default";
    
    public static final String CLUSTER_PREFIX  = PREFIX + "cluster.";
    public static final String CLUSTER_DEFINE  = CLUSTER_PREFIX + "define";
    public static final String CLUSTER_MEMBER  = CLUSTER_PREFIX + "member";
    public static final String CLUSTER_REORDER = CLUSTER_PREFIX + "reorder";
        
    // NOTE: These properties require a prefix which can only be determined at
    // runtime.  
    public static final String CLUSTER_PREFERENCE  = "preference.";    
    public static final String CLUSTER_MEMBERSHIP  = "preference.membership";
    public static final String CLUSTER_INSIDE      = "preference.inside";
    public static final String CLUSTER_DEFAULT     = "preference.default";
    public static final String CLUSTER_SUB_REORDER = "preference.reorder";
    
    private static final String [] defaults = new String [] {
            DIRECT_BACKLOG,         "255", 
            BACKLOG,                "50", 
            ACCEPT_TIMEOUT,         "60000", 
            DIRECT_LOCAL_TIMEOUT,   "1000", 
            
            STATISTICS_PRINT,       "false",
            STATISTICS_INTERVAL,    "0",
            
            SSH_OUT,                "false", 
            SSH_IN,                 "false",
            
            NIO,                    "false",
            DIRECT_SEND_BUFFER,     "-1",
            DIRECT_RECEIVE_BUFFER,  "-1",
            DIRECT_CACHE_IP,        "true",
            
            STUN,                   "false",
            UPNP,                   "false",            
            UPNP_PORT_FORWARDING,   "false", 
            
            NETWORKS_DEFAULT,       "auto",
            
            HUB_PORT,               "17878",
            HUB_SEND_BUFFER,        "-1",
            HUB_RECEIVE_BUFFER,     "-1",
            HUB_STATISTICS,         "false",
            HUB_STATS_INTERVAL,     "60000",
            HUB_VIRTUAL_PORT,       "42", 
            
            MODULES_DEFINE,         "direct,reverse,hubrouted", 
            MODULES_ORDER,          "direct,reverse,hubrouted",
            
            DISCOVERY_ALLOWED,      "false",
            DISCOVERY_PREFERRED,    "false",
            DISCOVERY_PORT,         "24545", 
            DISCOVERY_TIMEOUT,      "5000",    
            
            SL_SEND_BUFFER,         "-1", 
            SL_RECEIVE_BUFFER,      "-1",
            
            SL_FORCE,               "false",
            SL_TIMEOUT,             "10000",
            SL_RETRIES,             "6",
            
            ROUTED_BUFFER,          "65536",
            ROUTED_FRAGMENT,        "8176"            
    };

    private static TypedProperties defaultProperties;

    protected static final Logger logger = 
        LoggerFactory.getLogger("ibis.smartsockets.properties");
    
    private static TypedProperties getPropertyFile(String file) {

        if (logger.isInfoEnabled()) { 
            logger.info("Trying to load property file: " + file);
        }
        
        InputStream in = null;

        try {
            in = new FileInputStream(file);
            
            if (logger.isInfoEnabled()) {
                logger.info("File: " + file + " found!");
            }
        } catch (FileNotFoundException e) {
            if (logger.isInfoEnabled()) {
                logger.info("Property file \"" + file + "\" not found!");
            }         
        }
                
        if (in == null) {

            ClassLoader loader = ClassLoader.getSystemClassLoader();

            in = loader.getResourceAsStream(file);

            if (in != null) {
                if (logger.isInfoEnabled()) {
                    logger.info("Found property file in classpath: \""
                            + loader.getResource(file) + "\"");
                }
            } else {
                if (logger.isInfoEnabled()) {
                    logger.info("Property file \"" + file + "\" not found "
                            + "in classpath, giving up!");
                }
                return null;
            }
        }

        try {
            if (logger.isInfoEnabled()) {
                logger.info("Loading properties!");
            }
            
            TypedProperties p = new TypedProperties();
            p.load(in);

            if (logger.isInfoEnabled()) {
                logger.info(p.toString());
            }
                        
            return p;
        } catch (IOException e) {
            logger.warn("Error while loading property file: " + file, e);

            try {
                in.close();
            } catch (Exception x) {
                // ignore
            }
        }
        
        return null;
    }

    /**
     * Generates a SmartSocketsProperties object containing a configuration.
     * 
     * The properties in this configuration object are set a follows:
     * 
     * First, a set of hardcoded default properties is loaded.
     *  
     * Next, the "smartsockets.properties" file will be loaded (if found), overwriting the default settings it necessary. 
     * Set the "-Dsmartsockets.file=" property on the command line to load a different file.
     *
     * Finally, any command line properties starting with "smartsockets." will be loaded, overwriting existing settings if necessary.
     * 
     * @see #DEFAULT_FILE
     *   
     * @author Jason Maassen
     * @version 1.0 Nov 22, 2007
     * @since 1.0
     * 
     * 
     * 
     */
    public static TypedProperties getDefaultProperties() {
        
        if (defaultProperties == null) { 
            
            defaultProperties = new TypedProperties();

            // Start by inserting the default values.            
            for (int i=0;i<defaults.length;i+=2) { 
                defaultProperties.put(defaults[i], defaults[i+1]);
            }
      
            // Get the smartsockets related properties from the commandline. 
            TypedProperties system = 
                new TypedProperties(System.getProperties()).filter(PREFIX);

            // Check what property file we should load.
            String file = system.getProperty(FILE, DEFAULT_FILE); 

            // If the file is not explicitly set to null, we try to load it.
            // First try the filename as is, if this fails try with the
            // user home directory prepended.
            if (file != null) {
                TypedProperties fromFile = getPropertyFile(file);
                
                if (fromFile != null) {
                    defaultProperties.putAll(fromFile);
                } else {
                    if (!file.equals(DEFAULT_FILE)) { 
                        // If we fail to load the user specified file, we give an
                        // error, since only the default file may fail silently.                     
                        logger.error("User specified preferences \"" + file 
                                + "\" not found!");
                    }                                            
                }
            }
            
            // Finally, add the smartsockets related properties from the command
            // line to the result, possibly overriding entries from file or the 
            // defaults.            
            defaultProperties.putAll(system);
        } 
        
        return new TypedProperties(defaultProperties);        
    }
}
