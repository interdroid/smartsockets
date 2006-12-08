package smartsockets.direct;

import smartsockets.Properties;
import smartsockets.util.TypedProperties;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
//import java.util.StringTokenizer;

import org.apache.log4j.Logger;

import smartsockets.util.NetworkUtils;

public class NetworkPreference {
   
    private static final Logger logger = ibis.util.GetLogger
            .getLogger("smartsockets.network.preference");

    private Preference defaultPreference;
    private Preference networksPreference;
    
    private NetworkSet localNetworks;
    
    // The firewall rules for the local network. 
    private NetworkSet [] firewallAccept;
    private NetworkSet [] firewallDeny;
    private boolean firewallDefaultAccept = true;
    
    // This an optimization, so that we don't have to perform complicated tests 
    // if there are no rules in the fist place.
    private boolean firewallAcceptAll = true;
  
    /**
     * This constructor retrieves the 'ibis.connect.connect_preference'
     * property, which contains the preferred order in which network addresses
     * should be tried when a connection is attempted to a machine. Currently,
     * the following format is recognised:
     * 
     * X(,Y)*
     * 
     * where the variables (X and Y) have one of the following values:
     * 
     * "site" indicating all site-local addresses 
     * "link" indicating all link-local addresses 
     * "global" indicating all global addresses 
     * IP/MASK an IP address and a netmask (used to indicate a network range). 
     * IPMASK short notation for IP and simple netmask (e.g, 192.168.*.*)
     * 
     * When all matching network addresses have been tried, and the connection
     * setup has failed, all remaining network addresses which do not match any
     * preference rules are tried.
     * 
     * Examples:
     * 
     * try all site-local addresses first:
     * 
     * site
     * 
     * first try all link local addresses then global addresses:
     * 
     * link,global
     * 
     * first try a specific network, then all global addresses:
     * 
     * 192.168.0.0/255.255.255.0,global
     * 
     * same, using the shorter IPMASK notation:
     * 
     * 192.168.0.*,global
     * 
     * If the property is not set or the value could not be parsed, null is
     * returned.    
     */
    private NetworkPreference(IPAddressSet myAddress, TypedProperties p) {

        handleProperties(myAddress, p);
        
        if (logger.isInfoEnabled()) { 
            logger.info("My network is: "
                    + (localNetworks != null ? localNetworks.name : "N/A"));

            if (networksPreference == null) {
                logger.info("No network definitions found.");
            }
        }
    }

/*
    private static TypedProperties getPropertyFile() {

        //TypedProperties tp = new TypedProperties
        
        String file = Properties
                .stringProperty(smartsockets.direct.Properties.CONNECT_FILE);
        
        //System.err.println("GETTING FILE: "  + file);
        
        InputStream in = null;

        if (file != null) {
            try {
                in = new FileInputStream(file);
            } catch (FileNotFoundException e) {
                logger.info("Network preference file \"" + file
                        + "\" not found!");
            }
        }

        //System.err.println("CHECK STREAM: "  + in);
                
        if (in == null) {

            if (file == null) {
                file = DEFAULT_PREF_FILE;
            }

            //System.err.println("GETTING RESOURCE: "  + file);
                        
            ClassLoader loader = ClassLoader.getSystemClassLoader();
            
            //System.err.println("CLASSLOADER: "  + loader);
            
            in = loader.getResourceAsStream(file);

            if (in != null) {
                logger.info("Found network preferences in classpath: \""
                        + loader.getResource(file) + "\"");
            } else {
                logger.info("Network preferences \"" + file + "\" not found "
                        + "in classpath, giving up!");
                return null;
            }
        }

        try {

            Properties p = new Properties();
            p.load(in);

            return p;

        } catch (IOException e) {
            logger.warn("Error while loading network preference file: ", e);

            try {
                in.close();
            } catch (Exception x) {
                // ignore
            }
        }

        return null;
    }
*/

    private void handlePreference(Preference target, String [] property) {

        // NOTE: Assumes that "auto" is NOT present in property.        
        for (String p : property) {         
            if (p.equals("site")) {
                // all site local
                target.addSite();
            } else if (p.equals("link")) {
                // all link local
                target.addLink();
            } else if (p.equals("global")) {
                // all global
                target.addGlobal();
            } else if (p.equals("none")) {
                // no connection allowed
                target.addNone();
            } else {
                // explicit network rule
                target.addNetwork(getNetwork(p));
            }
        }
    }
    
    private void handlePreference(Preference target, IPAddressSet myAddress) {  
        
        InetAddress [] ads = myAddress.getAddresses();

        boolean haveLinkLocal = false;
                
        // Handle all our site local addresses first. Try to determine the exact 
        // subnet that we are connected to, and specifically add this network.
        for (int i=0;i<ads.length;i++) { 

            InetAddress a = ads[i];
            
            if (a.isSiteLocalAddress()) { 
                
                byte [] ab = a.getAddress();
                byte [] sub = new byte[ab.length];
                byte [] mask = new byte[ab.length];
                
                if (NetworkUtils.getSubnetMask(ab, sub, mask)) { 
                    target.addNetwork(new Network(sub, mask));
                } else { 
                    if (logger.isInfoEnabled()) { 
                        logger.info("Failed to get subnet/mask for: " 
                                + NetworkUtils.ipToString(a) + "!");
                    }
                }
            } 
        }

        // Followed by link-local addresses, provided that we have one 
        // ourselves...
        // TODO: do we really want this here, or at the end ?? 
        for (int i=0;i<ads.length;i++) { 

            InetAddress a = ads[i];
            
            if (a.isLinkLocalAddress()) { 
                target.addLink();
                haveLinkLocal = true;
                break;
            } 
        }

        // Finally accept all target global, site local, and (optionally) link
        // local addresses (in that order!!)
        target.addGlobal();        
        target.addSite();        
        
        if (!haveLinkLocal) {
            target.addLink();
        }
    }
    
    private void handlePreference(Preference target, IPAddressSet myAddress, 
            String [] property) {

        for (String p : property) {
            if (p.equalsIgnoreCase("auto")) { 
                if (logger.isInfoEnabled()) { 
                    logger.info("Using automatic network setup.");
                }
                handlePreference(target, myAddress);
                return;
            }            
        }
            
        if (logger.isInfoEnabled()) { 
            logger.info("Using manual network setup.");
        }
        
        handlePreference(target, property);
    }

    private NetworkSet [] getNetworks(String [] names, LinkedList<NetworkSet> nws) { 
    
        ArrayList<NetworkSet> tmp = new ArrayList<NetworkSet>(); 
        
        for (String name: names) { 
            
            boolean found = false;
            
            for (NetworkSet s : nws) {
                if (name.equals(s.name)) { 
                    found = true;
                    tmp.add(s);
                    break;
                }
            }
            
            if (!found) { 
                logger.warn("Network " + name + " removed from firewall "
                        + "rule, since it is not defined!");
            }
        }
        
        if (tmp.size() > 0) { 
            return tmp.toArray(new NetworkSet[0]);
        } else { 
            return null;
        }    
    }
    
    // This will overwrite any previous preferences.
    private void handleProperties(IPAddressSet myAddress, TypedProperties p) {

        // Start by getting the default network setting
        String [] def = p.getStringList(Properties.NETWORKS_DEFAULT);

        if (def == null || def.length == 0) {
            if (logger.isInfoEnabled()) { 
                logger.info("No default network setup definitions found.");
            }
            def = new String [] { "auto" };
        }
        
        defaultPreference = new Preference("default", false);
        handlePreference(defaultPreference, myAddress, def);           

        if (logger.isInfoEnabled()) { 
            logger.info(defaultPreference.toString());
        }
        
        // Get the name of the local network (if available). This will normally 
        // not be available, and a range will be used instead. However, in some
        // simulated scenario's this apprach comes in handy
        String name = p.getProperty(Properties.NETWORKS_MEMBER);

        // Get a list of all the networks defined in the properties.
        String [] networks = p.getStringList(Properties.NETWORKS_DEFINE, ",");

        if (networks == null || networks.length == 0) {
            // No further rules, so just remember the local network name...
            localNetworks = new NetworkSet(name);
            return;
        }

        // Now go through all of the network definitions. If we find our 
        // own network here we remember the detailed rules. For the other 
        // network definitions  we just rememeber the names/ranges in the 
        // 'other' list, since we may need them afterwards when we are handeling
        // the firewall rules for out local network.
        LinkedList<NetworkSet> other = new LinkedList<NetworkSet>();
        String[][] firewall = new String[3][];
        
        for (int i=0;i<networks.length;i++) { 
            handleNetworkDefinition(myAddress, name, networks[i], p, other, firewall);
        }
        
        // If no local network is defined, there cannot be any detailed firewall
        // rules either...
        if (localNetworks == null) { 
            return;
        }
        
        // Now handle any firewall rules specified for our network. We can only
        // do this after parsing all network definitions, since these rules use 
        // the 'human-readable' names of the networks.
    
        // Check if there is a default rule
        if (firewall[2] != null && firewall[2].length > 0) { 
            if (firewall[2][0].equalsIgnoreCase("accept")) { 
                // nothing to do
            } else if (firewall[2][0].equalsIgnoreCase("deny")) { 
                firewallDefaultAccept = false;
                firewallAcceptAll = false;
            } else { 
                logger.warn("Property \"smartsockets.networks.firewall." 
                        + localNetworks.name + ".default\" has illegal value: " 
                        + firewall[2][0] + " (must be \"accept\" or \"deny\")");
            }
            
            if (firewall[2].length > 1) { 
                logger.warn("Property \"smartsockets.networks.firewall." 
                        + localNetworks.name + ".default\" may only have a " 
                        + "single value!");
            }
        }
       
        // Check if there are accept rules, but only if the default is 'deny'
        if (!firewallDefaultAccept && firewall[0] != null && firewall[0].length > 0) { 
            firewallAccept = getNetworks(firewall[0], other);
        }
        
        // Check if there are deny rules, but only if the default is "accept"
        if (firewallDefaultAccept && firewall[1] != null && firewall[1].length > 0) { 
            firewallDeny = getNetworks(firewall[1], other);
            firewallAcceptAll = false;
        }
    }

    private void handleNetworkDefinition(IPAddressSet myAddress, String name,
            String currentNetwork, TypedProperties p, 
            LinkedList<NetworkSet> other, String[][] firewall) {

        boolean myNetwork = false;

        String prefix = Properties.NETWORKS_PREFIX + currentNetwork + ".";
        String [] range = p.getStringList(prefix + Properties.NW_RANGE); 
       
        NetworkSet nws = null;
        
        if (range.length > 0) { 
            nws = parseNetworkSet(currentNetwork, range);
        } else { 
            nws = new NetworkSet(currentNetwork);
        }
        
        if (nws.inNetwork(myAddress.getAddresses(), name)) { 
            localNetworks = nws;
            myNetwork = true;
        } else { 
            other.addLast(nws);    
        }
        
        if (logger.isInfoEnabled()) { 
            logger.info("Network name: " + currentNetwork + 
                    (myNetwork ? " (MY NETWORK)": ""));
            logger.info("  range: " + 
                    (range.length > 0 ? Arrays.deepToString(range) : "none"));       
        }
        
        if (!myNetwork) {
            // We are not interested in other networks' options.
            return;
        }

        // We are handling our local network here!
        
        // Check for any rules that specify how we should connect inside
        // our own network.
        String [] inside = p.getStringList(prefix + Properties.NW_PREFERENCE_INSIDE);

        if (inside.length > 0) {

            if (logger.isInfoEnabled()) { 
                logger.info("  in network use: " + Arrays.deepToString(inside));
            }

            networksPreference = new Preference("lan", true);
            handlePreference(networksPreference, inside);
        }

        // Check for any rules that specify how to connect to other networks
        String [] def = p.getStringList(prefix + Properties.NW_PREFERENCE_DEFAULT);

        if (def.length > 0) {

            if (logger.isInfoEnabled()) { 
                logger.info("  default use: " + Arrays.deepToString(def));
            }

            defaultPreference = new Preference("default", false);
            handlePreference(defaultPreference, def);
        }
        
        // Check for any 'firewall' rule that specifies who we should accept.
        firewall[0] = p.getStringList(prefix + Properties.NW_FIREWALL_ACCEPT);

        if (firewall[0].length > 0) { 
            if (logger.isInfoEnabled()) { 
                logger.info("  firewall accept : " + Arrays.deepToString(firewall[0]));
            }
        }
        
        // Check for any 'firewall' rule that specifies who we should deny.
        firewall[1] = p.getStringList(prefix + Properties.NW_FIREWALL_DENY);

        if (firewall[1].length > 0) { 
            if (logger.isInfoEnabled()) { 
                logger.info("  firewall deny   : " + Arrays.deepToString(firewall[1]));
            }
        }
        
        // Check for any 'firewall' rule that specifies the 'default'.
        firewall[2] = p.getStringList(prefix + Properties.NW_FIREWALL_DEFAULT);

        if (firewall[2].length > 0) { 
            if (logger.isInfoEnabled()) { 
                logger.info("  firewall default: " + Arrays.deepToString(firewall[2]));
            }
        }
    }
    /*
    private boolean parseNetworkRange(IPAddressSet myAddress, String name, 
            String [] range, boolean setLocal, LinkedList <NetworkSet> other) {

        ArrayList<Network> inc = new ArrayList<Network>();
        ArrayList<Network> ex = new ArrayList<Network>();
        
        for (int i=0;i<range.length;i++) {
            if (range[i].startsWith("!")) {
                ex.add(getNetwork(range[i].substring(1)));
            } else { 
                inc.add(getNetwork(range[i]));
            }            
        }
            
        NetworkSet nws = new NetworkSet(name, 
                inc.toArray(new Network[inc.size()]),  
                ex.toArray(new Network[ex.size()])); 
        
        InetAddress [] ads = myAddress.getAddresses();
        
        if (nws.inNetwork(ads)) { 
            localNetworks = nws;
            return true;
        }
        
        other.addLast(nws);
        return false;
    }
      */  
    private NetworkSet parseNetworkSet(String name, String [] range) {

        ArrayList<Network> inc = new ArrayList<Network>();
        ArrayList<Network> ex = new ArrayList<Network>();
        
        for (int i=0;i<range.length;i++) {
            if (range[i].startsWith("!")) {
                ex.add(getNetwork(range[i].substring(1)));
            } else { 
                inc.add(getNetwork(range[i]));
            }            
        }
            
        return new NetworkSet(name, 
                inc.toArray(new Network[inc.size()]),  
                ex.toArray(new Network[ex.size()]));         
    }
    
    private Network getNetwork(String range) { 

        int index = range.indexOf('/');

        byte [] sub;
        byte [] mask;

        if (index != -1) {
            // IP/MASK format
            sub = addressToBytes(range.substring(0, index));
            mask = addressToBytes(range.substring(index + 1));
        } else {          
            // IPMASK or SINGLE format                 
            range = range.replaceAll("\\*", "0");
                
            sub = addressToBytes(range);                
            mask = new byte[sub.length];                               
                
            for (int i=0;i<sub.length;i++) {
                if (sub[i] != 0) {
                    mask[i] = (byte) 255;
                }
            }            
        }

        return new Network(sub, mask);                
    }
        
/*
    private boolean inNetwork(IPAddressSet myAddress, String range) {

        // either IP/MASK or IPMASK format
        int index = range.indexOf('/');

        byte[] sub;
        byte[] mask;

        if (index != -1) {
            // IP/MASK format
            sub = addressToBytes(range.substring(0, index));
            mask = addressToBytes(range.substring(index + 1));
        } else {
            // IPMASK format
            // TODO: implement
            sub = mask = null;   
        
        return (inNetwork(include, ads) && !inNetwork(exclude, ads)); 

        }

        if (inNetwork(myAddress.getAddresses(), sub, mask)) {
            networkSubnet = sub;
            networkMask = mask;
            return true;
        } else {
            return false;
        }
    }
*/

    private static byte[] addressToBytes(String address) {

        try {
            InetAddress tmp = InetAddress.getByName(address);
            return tmp.getAddress();
        } catch (UnknownHostException e) {
            return new byte[0];
        }
    }

    public InetSocketAddress[] sort(InetSocketAddress[] ads, boolean inPlace) {

        // Check if the target belongs to our network.
        if (networksPreference != null && localNetworks.inNetwork(ads, null)) { 
            return networksPreference.sort(ads, inPlace);
        }

        return defaultPreference.sort(ads, inPlace);
    }

    public InetAddress[] sort(InetAddress[] ads, boolean inPlace) {

        // Check if the target belongs to our network.
        if (networksPreference != null && localNetworks.inNetwork(ads, null)) { 
            return networksPreference.sort(ads, inPlace);
        }

        return defaultPreference.sort(ads, inPlace);
    }

    public boolean accept(InetAddress[] ads, String name) { 
        
        // Accept if there are no rules or if the source is local
        if (firewallAcceptAll || localNetworks.inNetwork(ads, name)) { 
            return true;
        }
    
        if (firewallDefaultAccept) {     
            // If the default is accept, we only have to check the 'deny' list
            for (NetworkSet nws : firewallDeny) { 
                if (nws.inNetwork(ads, name)) { 
                    return false;
                }
            }
            
            return true;
        } else { 
            // If the default is deny, we only have to check the 'accept' list
            for (NetworkSet nws : firewallAccept) { 
                if (nws.inNetwork(ads, name)) { 
                    return true;
                }
            }
            
            return false;
        }
    }
    
    public boolean accept(InetSocketAddress[] ads, String name) { 
        // Check if we are allowed to accept an incoming connection from this 
        // machine. 
        // Accept if there are no rules or if the source is local
        if (firewallAcceptAll || localNetworks.inNetwork(ads, name)) { 
            return true;
        }
    
        if (firewallDefaultAccept) {     
            // If the default is accept, we only have to check the 'deny' list
            for (NetworkSet nws : firewallDeny) { 
                if (nws.inNetwork(ads, name)) { 
                    return false;
                }
            }
            
            return true;
        } else { 
            // If the default is deny, we only have to check the 'accept' list
            for (NetworkSet nws : firewallAccept) { 
                if (nws.inNetwork(ads, name)) { 
                    return true;
                }
            }
            
            return false;
        }
    }
  
    
    public String getNetworkName() {
        return (localNetworks == null ? null : localNetworks.name);
    }

    public String toString() {
        return defaultPreference.toString();
    }
    
    public static NetworkPreference getPreference(IPAddressSet myAddress, 
            TypedProperties p) { 
        
        return new NetworkPreference(myAddress, p);
    }
}
