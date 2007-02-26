package smartsockets.virtual.modules;



import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

import smartsockets.direct.DirectSocketAddress;
import smartsockets.hub.servicelink.CallBack;
import smartsockets.hub.servicelink.ServiceLink;
import smartsockets.util.TypedProperties;
import smartsockets.virtual.ModuleNotSuitableException;
import smartsockets.virtual.VirtualSocket;
import smartsockets.virtual.VirtualSocketAddress;
import smartsockets.virtual.VirtualSocketFactory;

public abstract class ConnectModule implements CallBack {
    
    protected static final Logger statslogger = 
        ibis.util.GetLogger.getLogger("smartsockets.statistics");
    
    public final String module;
    
    public final boolean requiresServiceLink;
    
    protected VirtualSocketFactory parent;
    protected Logger logger;
    protected ServiceLink serviceLink;     
    protected Map<String, Object> properties; 
    
    protected String name;
    
    protected long incomingConnections; 
    protected long acceptedIncomingConnections; 
    protected long rejectedIncomingConnections; 
    protected long failedIncomingConnections; 
    
    protected long outgoingConnectionAttempts;
    protected long acceptedOutgoingConnections; 
    protected long failedOutgoingConnections; 
    
    protected long connectTime;    
    protected long failedTime;
    protected long notAllowed;
    
    protected ConnectModule(String name, boolean requiresServiceLink) { 
        this(name, requiresServiceLink, null);
    }
    
    protected ConnectModule(String name, boolean requiresServiceLink, 
            Map<String, Object> p) {
        
        this.module = name;
        this.requiresServiceLink = requiresServiceLink;
        
        if (p == null) {         
            this.properties = new HashMap<String, Object>();
        } else { 
            this.properties = p;
        }
            
        if (!properties.containsKey("connect.module.name")) { 
            properties.put("connect.module.name", name);
        } 
        
        if (!properties.containsKey("connect.module.type")) {            
            if (!requiresServiceLink) { 
                properties.put("connect.module.type", new String [] {"direct"});
            } else { 
                properties.put("connect.module.type", new String [] {"indirect"});
            }
        } 
    }
        
    public void init(VirtualSocketFactory p, String name, TypedProperties properties, 
            Logger l) throws Exception {
        
        this.name = name;
        parent = p;        
        logger = l;               
                
        if (l.isInfoEnabled()) {         
            l.info("Initializing module: " + name + " -> " + module);
        }
                        
        // Now perform the implementation-specific initialization.
        initModule(properties);
    }
    
    public String getName() { 
        return name;
    }
    
    public void startModule(ServiceLink sl) throws Exception { 
        
        if (requiresServiceLink) { 
            
            if (sl == null) {
                throw new Exception("Failed to initialize module: " + module 
                        + " (service link required)");                
            } 
              
            serviceLink = sl;
            serviceLink.register(module, this);        
        }   
        
        startModule();
    }
    
    public void gotMessage(DirectSocketAddress src, DirectSocketAddress proxy, 
            int opcode, byte [][] message) {
        // Note: Default implementation. Should be extended by any module 
        // which requires use of service links         
        logger.warn("Module: "+ module + " got unexpected message from " + src 
                + "@" + proxy + ", " + opcode + ", " + message);
    }
    
    // Checks if the string "target" is found in the string "csv". The string 
    // csv is expected to contain whitespace/comma seperated values. This method 
    // only returns true iff "?target?" is a substring of "csv", where the '?'
    // characters are either comma, space, or the beginning/end of the string. 
    private boolean contains(String csv, String target) { 
        if (logger.isInfoEnabled()) {
            logger.info("Checking if \"" + target + "\" is part of \"" + csv + "\"");
        }
                        
        int start = 0;
        int end = target.length();
        
        while (end <= csv.length()) {         
            start = csv.indexOf(target, start);
            end = start + target.length();
                        
            if (start == -1) {
                if (logger.isInfoEnabled()) {
                    logger.info("\"" + target + "\" is NOT part of \"" + csv + "\"");
                }
                return false;
            }
        
            boolean startOK = (start == 0 || csv.charAt(start-1) == ',' 
                || csv.charAt(start-1) == ' ');
            
            boolean endOK = (end == csv.length() 
                                || csv.charAt(end+1) == ',' 
                                || csv.charAt(end+1) == ' ');
            
            if (startOK && endOK) {
                if (logger.isInfoEnabled()) {
                    logger.info("\"" + target + "\" IS part of \"" + csv + "\"");
                }
                return true;
            }
        
            start += 1;            
        } 
        
        if (logger.isInfoEnabled()) {
            logger.info("\"" + target + "\" IS NOT part of \"" + csv + "\"");
        }
        return false;
    }
    
    private boolean contains(String csv, String [] targets) {
        
        for (int i=0;i<targets.length;i++) { 
            
            if (contains(csv, targets[i])) { 
                return true;
            }
        }
        
        return false;
    }
        
    
    public boolean matchRuntimeRequirements(Map requirements) {
        
        if (requirements == null) { 
            return true;
        }
        
        /* 
         * We first check if this module or this type of module is explicitly 
         *  switched off.
         */ 

        // Check if there are explicitly named modules to skip....
        String tmp = (String) requirements.get("connect.module.skip");
        
        if (tmp != null) { 
            if (contains(tmp, module)) { 
                return false;
            } // else, continue with other checks            
        }

        // Check if there is a certain type of modules to skip....
        String [] myTypes = (String []) properties.get("connect.module.type");
        
        tmp = (String) requirements.get("connect.module.type.skip");
        
        if (tmp != null) {             
            if (contains(tmp, myTypes)) { 
                return false;
            } // else, continue with other checks
        }

        /* 
         * Next, we check if there is a certain set of modules which are 
         * explicitly selected. This implies that all other types are switched 
         * off.
         */ 

        boolean selectionExists = false;
        boolean selected = false;
        
        // Check if there are certain modules are explicitly selected.
        tmp = (String) requirements.get("connect.module.allow");
        
        if (tmp != null) { 
            selectionExists = true;
            selected = contains(tmp, module); 
        }
        
        // Check if there are certain module types explicitly selected.
        if (!selected) { 
            tmp = (String) requirements.get("connect.module.type.allow");
        
            if (tmp != null) {
                selectionExists = true;
                selected = contains(tmp, myTypes); 
            }
        } 
        
        if (selectionExists && !selected) {
            // If we do not belong to the happy few, return false.  
            return false;
        } 
        
        // Finally, we allow the module implementations themselves to match 
        // any further requirements that may exist.        
        return matchAdditionalRuntimeRequirements(requirements);
    }
    
    public String toString() { 
        return name;
    }
    
    public void success(long time) { 
        connectTime += time;
    }
    
    public void failed(long time) { 
        failedTime += time;
    }
    
    public void notAllowed() { 
        notAllowed++;
    }
    
    public abstract void initModule(TypedProperties prop) throws Exception; 

    public abstract void startModule() throws Exception; 

    public abstract boolean matchAdditionalRuntimeRequirements(Map requirements);
    
    public abstract DirectSocketAddress getAddresses(); 
    
    public abstract VirtualSocket connect(VirtualSocketAddress target, 
            int timeout, Map<String, Object> properties) 
        throws ModuleNotSuitableException, IOException;

    public void printStatistics(String prefix) {
        
        if (statslogger.isInfoEnabled()) {
              statslogger.info(prefix + " -> " + name +" out: ["
                      + outgoingConnectionAttempts + " / "  
                      + acceptedOutgoingConnections + " (" + connectTime + " ms.) / "
                      + failedOutgoingConnections + " (" + failedTime + " ms.) / " 
                      + notAllowed + "] in: [" 
                      + incomingConnections + "/" 
                      + acceptedIncomingConnections + "/" 
                      + rejectedIncomingConnections + "/"
                      + failedIncomingConnections + "]");
          }        
    }
        
}
