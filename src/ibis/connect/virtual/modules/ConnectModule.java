package ibis.connect.virtual.modules;

import ibis.connect.direct.SocketAddressSet;

import ibis.connect.virtual.ModuleNotSuitableException;
import ibis.connect.virtual.VirtualSocket;
import ibis.connect.virtual.VirtualSocketAddress;
import ibis.connect.virtual.VirtualSocketFactory;

import ibis.connect.virtual.service.ServiceLink;
import ibis.connect.virtual.service.CallBack;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

public abstract class ConnectModule implements CallBack {
            
    public final String name;
    public final boolean requiresServiceLink;
    
    protected VirtualSocketFactory parent;
    protected Logger logger;
    protected ServiceLink serviceLink;     
    protected Map properties; 
        
    protected ConnectModule(String name, boolean requiresServiceLink) { 
        this(name, requiresServiceLink, null);
    }
    
    protected ConnectModule(String name, boolean requiresServiceLink, Map p) { 
        this.name = name;
        this.requiresServiceLink = requiresServiceLink;
        
        if (p == null) {         
            this.properties = new HashMap();
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
        
    public void init(VirtualSocketFactory p, Map properties, Logger l) throws Exception {
        
        parent = p;        
        logger = l;               
                
        l.info("Initializing module: " + name);
                        
        // Now perform the implementation-specific initialization.
        initModule(properties);
    }
    
    public void startModule(ServiceLink sl) throws Exception { 
        
        if (requiresServiceLink) { 
            
            if (sl == null) {
                throw new Exception("Failed to initialize module: " + name 
                        + " (service link required)");                
            } 
              
            serviceLink = sl;
            serviceLink.register(name, this);        
        }   
        
        startModule();
    }
    
    public void gotMessage(SocketAddressSet src, SocketAddressSet proxy, 
            int opcode, String message) {
        // Note: Default implementation. Should be extended by any module 
        // which requires use of service links         
        logger.warn("Module: "+ name + " got unexpected message from " + src 
                + "@" + proxy + ", " + opcode + ", " + message);
    }
    
    // Checks if the string "target" is found in the string "csv". The string 
    // csv is expected to contain whitespace/comma seperated values. This method 
    // only returns true iff "?target?" is a substring of "csv", where the '?'
    // characters are either comma, space, or the beginning/end of the string. 
    private boolean contains(String csv, String target) { 
        
        logger.warn("Checking if \"" + target + "\" is part of \"" + csv + "\"");
                        
        int start = 0;
        int end = target.length();
        
        while (end <= csv.length()) {         
            start = csv.indexOf(target, start);
            end = start + target.length();
                        
            if (start == -1) {
                logger.warn("\"" + target + "\" is NOT part of \"" + csv + "\"");                
                return false;
            }
        
            boolean startOK = (start == 0 || csv.charAt(start-1) == ',' 
                || csv.charAt(start-1) == ' ');
            
            boolean endOK = (end == csv.length() 
                                || csv.charAt(end+1) == ',' 
                                || csv.charAt(end+1) == ' ');
            
            if (startOK && endOK) { 
                logger.warn("\"" + target + "\" IS part of \"" + csv + "\"");
                return true;
            }
        
            start += 1;            
        } 
        
        logger.warn("\"" + target + "\" IS NOT part of \"" + csv + "\"");
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
            if (contains(tmp, name)) { 
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
            selected = contains(tmp, name); 
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
    
    public abstract void initModule(Map properties) throws Exception; 

    public abstract void startModule() throws Exception; 

    public abstract boolean matchAdditionalRuntimeRequirements(Map requirements);
    
    public abstract SocketAddressSet getAddresses(); 
    
    public abstract VirtualSocket connect(VirtualSocketAddress target, int timeout,
            Map properties) throws ModuleNotSuitableException, IOException;        
}
