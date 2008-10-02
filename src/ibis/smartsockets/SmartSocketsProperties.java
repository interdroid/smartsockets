package ibis.smartsockets;

import ibis.smartsockets.util.TypedProperties;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  
 * TODO Description 
 *  
 * @author Jason Maassen
 * @version 1.0 Nov 22, 2007
 * @since 1.0
 * 
 */
public class SmartSocketsProperties {

    public static final String DEFAULT_FILE = "smartsockets.properties";
    
    public static final String PREFIX = "smartsockets.";
   
    public static final String FILE                = PREFIX + "file";
    public static final String START_HUB           = PREFIX + "start.hub";  
    
    public static final String STATISTICS_PRINT    = PREFIX + "statistics";
    public static final String STATISTICS_PREFIX   = PREFIX + "statistics.prefix";    
    public static final String STATISTICS_INTERVAL = PREFIX + "statistics.interval";
    public static final String DETAILED_EXCEPTIONS = PREFIX + "detailed.exceptions";
    
    public static final String NIO        = PREFIX + "nio";
    public static final String PORT_RANGE = PREFIX + "port.range";    
    public static final String BACKLOG    = PREFIX + "backlog";
   
    public static final String CONNECT_TIMEOUT = PREFIX + "timeout.connect";  
    public static final String ACCEPT_TIMEOUT  = PREFIX + "timeout.accept";      
    
    public static final String EXTERNAL_PREFIX      = PREFIX + "external.";
    public static final String UPNP                 = EXTERNAL_PREFIX + "upnp";
    public static final String UPNP_PORT_FORWARDING = EXTERNAL_PREFIX + "upnp.forwarding";        
    public static final String STUN                 = EXTERNAL_PREFIX + "stun";   
    public static final String STUN_SERVERS         = EXTERNAL_PREFIX + "stun.servers";    
    public static final String EXTERNAL_MANUAL      = EXTERNAL_PREFIX + "manual";
    
    public static final String DISCOVERY_PREFIX    = PREFIX + "discovery.";
    public static final String DISCOVERY_ALLOWED   = DISCOVERY_PREFIX + "allowed"; 
    public static final String DISCOVERY_PREFERRED = DISCOVERY_PREFIX + "preferred"; 
    public static final String DISCOVERY_LISTED    = DISCOVERY_PREFIX + "forcelisted"; 
    public static final String DISCOVERY_PORT      = DISCOVERY_PREFIX + "port";
    public static final String DISCOVERY_TIMEOUT   = DISCOVERY_PREFIX + "timeout";
    
    public static final String HUB_PREFIX           = PREFIX + "hub.";
    public static final String HUB_ADDRESSES        = HUB_PREFIX + "addresses";    
    public static final String HUB_NAME             = HUB_PREFIX + "name";
    public static final String HUB_CLUSTERS         = HUB_PREFIX + "clusters";       
    public static final String HUB_PORT             = HUB_PREFIX + "port";                 
    public static final String HUB_SSH_ALLOWED      = HUB_PREFIX + "ssh";     
    public static final String HUB_SEND_BUFFER      = HUB_PREFIX + "sendbuffer";
    public static final String HUB_RECEIVE_BUFFER   = HUB_PREFIX + "receivebuffer";
    public static final String HUB_STATISTICS       = HUB_PREFIX + "statistics";                 
    public static final String HUB_STATS_INTERVAL   = HUB_PREFIX + "statistics.interval";  
    
    public static final String HUB_DELEGATE         = HUB_PREFIX + "delegate";
    public static final String HUB_DELEGATE_ADDRESS = HUB_PREFIX + "delegate.address";    
    public static final String HUB_VIRTUAL_PORT     = HUB_PREFIX + "virtualPort";
    
    public static final String HUB_ADDRESS_FILE     = HUB_PREFIX + "addressfile";
        
    public static final String SL_PREFIX         = PREFIX + "servicelink.";        
    public static final String SL_SEND_BUFFER    = SL_PREFIX + "sendbuffer";
    public static final String SL_RECEIVE_BUFFER = SL_PREFIX + "receivebuffer";
    public static final String SL_FORCE          = SL_PREFIX + "force";
    public static final String SL_TIMEOUT        = SL_PREFIX + "timeout";
    public static final String SL_RETRIES        = SL_PREFIX + "retries";
    
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
            
            NIO,             "false",
            DIRECT_SEND_BUFFER,     "-1",
            DIRECT_RECEIVE_BUFFER,  "-1",
            
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
     * @return
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
