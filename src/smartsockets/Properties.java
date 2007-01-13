package smartsockets;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.apache.log4j.Logger;

import smartsockets.util.TypedProperties;


public class Properties {

    public static final String DEFAULT_FILE = "smartsockets.properties";
    
    public static final String PREFIX = "smartsockets.";
    
    public static final String FILE                = PREFIX + "file";
    public static final String ROUTERS             = PREFIX + "routers";  
    public static final String STATISTICS_INTERVAL = PREFIX + "statistics.interval";
        
    public static final String BACKLOG             = PREFIX + "backlog";  
    public static final String DIRECT_BACKLOG      = PREFIX + "direct.backlog";  
    public static final String TIMEOUT             = PREFIX + "timeout";  
    public static final String LOCAL_TIMEOUT       = PREFIX + "timeout.local";  
       
    public static final String CREDITS             = PREFIX + "credits";
    
    public static final String SSH_PREFIX          = PREFIX + "ssh.";
    public static final String SSH_IN              = SSH_PREFIX + "in.allow";
    public static final String SSH_OUT             = SSH_PREFIX + "out.allow";
    public static final String FORCE_SSH_OUT       = SSH_PREFIX + "out.force";
    
    public static final String NETWORKS_PREFIX = PREFIX + "networks.";    
    
    public static final String NIO             = NETWORKS_PREFIX + "nio";
    public static final String PORT_RANGE      = NETWORKS_PREFIX + "port_range";    
    public static final String IN_BUF_SIZE     = NETWORKS_PREFIX + "input_buffer";
    public static final String OUT_BUF_SIZE    = NETWORKS_PREFIX + "output_buffer";
    
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
    
    public static final String EXTERNAL_PREFIX      = NETWORKS_PREFIX + "external.";
    public static final String UPNP                 = EXTERNAL_PREFIX + "upnp";
    public static final String UPNP_PORT_FORWARDING = EXTERNAL_PREFIX + "upnp.forwarding";        
    public static final String STUN                 = EXTERNAL_PREFIX + "stun";   
    public static final String STUN_SERVERS         = EXTERNAL_PREFIX + "stun.servers";    
    public static final String EXTERNAL_MANUAL      = EXTERNAL_PREFIX + "manual";
    
    public static final String MODULES_PREFIX = PREFIX + "modules.";
    public static final String MODULES_DEFINE = MODULES_PREFIX + "define";
    public static final String MODULES_ORDER  = MODULES_PREFIX + "order";
    public static final String MODULES_SKIP   = MODULES_PREFIX + "skip";
    
    public static final String DISCOVERY_PREFIX    = PREFIX + "discovery.";
    public static final String DISCOVERY_PREFERRED = DISCOVERY_PREFIX + "preferred"; 
    public static final String DISCOVERY_ALLOWED   = DISCOVERY_PREFIX + "allowed"; 
    public static final String DISCOVERY_PORT      = DISCOVERY_PREFIX + "port";
    public static final String DISCOVERY_TIMEOUT   = DISCOVERY_PREFIX + "timeout";
    
    public static final String HUB_PREFIX         = PREFIX + "hub.";
    public static final String HUB_ADDRESS        = HUB_PREFIX + "address";
    public static final String HUB_SIMPLE_NAME    = HUB_PREFIX + "simple_name";
    public static final String HUB_CLUSTERS       = HUB_PREFIX + "clusters";       
    public static final String HUB_PORT           = HUB_PREFIX + "port";                 
    public static final String HUB_SSH_ALLOWED    = HUB_PREFIX + "ssh";     
    public static final String HUB_KNOWN_HUBS     = HUB_PREFIX + "known";
    
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
            BACKLOG,                "100", 
            TIMEOUT,                "5000", 
            LOCAL_TIMEOUT,          "100", 
            
            SSH_OUT,                "false", 
            SSH_IN,                 "false",
            
            NIO,                    "false", 
            STUN,                   "false",
            UPNP,                   "false",            
            UPNP_PORT_FORWARDING,   "false", 
            
            IN_BUF_SIZE,            "65536", 
            OUT_BUF_SIZE,           "65536",
            
            NETWORKS_DEFAULT,       "auto",
            
            HUB_PORT,               "17878",
            
            MODULES_DEFINE,         "direct,reverse,splice,hubrouted", 
            MODULES_ORDER,          "direct,reverse,splice,hubrouted",
            
            DISCOVERY_PREFERRED,    "false",
            DISCOVERY_ALLOWED,      "true",
            DISCOVERY_PORT,         "24545", 
            DISCOVERY_TIMEOUT,      "5000",    
            
            CREDITS,                "100"
    };

    private static TypedProperties defaultProperties;

    protected static Logger logger =         
            ibis.util.GetLogger.getLogger("smartsockets.properties");
    
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
            if (file != null) {
                
                TypedProperties fromFile = getPropertyFile(file);
                
                if (fromFile == null) { 
	            if (!file.equals(DEFAULT_FILE)) { 
                    	// If we fail to load the user specified file, we give an
                    	// error, since only the default file may fail silently.                     
                    	logger.error("User specified preferences \"" + file 
                        	    + "\" not found!");
                    }                                            
                } else {                  
                    // If we managed to load the file, we add the properties to 
                    // the 'defaultProperties' possibly overwriting defaults.
                    defaultProperties.putAll(fromFile);
                }
            }
            
            // Finally, add the smartsockets related properties from the command
            // line to the result, possibly overriding entries from file or the 
            // defaults.            
            defaultProperties.putAll(system);
        } 
        
        return defaultProperties;        
    }
}
