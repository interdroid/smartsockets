package smartsockets.direct;

import smartsockets.Properties;
import smartsockets.util.TypedProperties;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;

import smartsockets.util.NetworkUtils;

public class NetworkPreference {
   
    static Logger logger = ibis.util.GetLogger
            .getLogger(NetworkPreference.class.getName());

    private TypedProperties properties;
    
    private Preference defaultPreference;

    private String networkName;

    private Preference networksPreference;

    //private byte[] networkSubnet;
    //private byte[] networkMask;

    private Network [] include;
    private Network [] exclude;
    
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
     * "site" indicating all site-local addresses "link" indicating all
     * link-local addresses "global" indicating all global addresses IP/MASK an
     * IP address and a netmask (used to indicate a network range). IPMASK short
     * notation for IP and simple netmask (e.g, 192.168.*.*)
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
        
        if (defaultPreference == null) {
            logger.info("No default network setup definitions found.");
            defaultPreference = new Preference("default", false);
        }

        logger.info("My cluster is: "
                + (networkName != null ? networkName : "N/A"));

        if (networksPreference == null) {
            logger.info("No cluster definitions found.");
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

        for (int i=0;i<property.length;i++) {         
            if (property[i].equals("site")) {
                // all site local
                target.addSite();
            } else if (property[i].equals("link")) {
                // all link local
                target.addLink();
            } else if (property[i].equals("global")) {
                // all global
                target.addGlobal();
            } else {
                target.addNetwork(getNetwork(property[i]));
            }
        }
    }

    // This will overwrite any previous preferences.
    private void handleProperties(IPAddressSet myAddress, TypedProperties p) {

        String [] def = p.getStringList(Properties.NETWORKS_DEFAULT);

        if (def != null && def.length > 0) {
            defaultPreference = new Preference("default", false);
            handlePreference(defaultPreference, def);
        }

        String [] networks = p.getStringList(Properties.NETWORKS_DEFINE, ",");

        if (networks == null || networks.length == 0) {
            return;
        }

        for (int i=0;i<networks.length;i++) { 
            handleClusterProperties(myAddress, networks[i], p);
        }
    }

    private void handleClusterProperties(IPAddressSet myAddress, String name,
            TypedProperties p) {

        boolean myNetwork = false;

        String prefix = Properties.NETWORKS_PREFIX + name + ".";
        String [] range = p.getStringList(prefix + Properties.NW_RANGE); 
        String [] network = p.getStringList(prefix + Properties.NW_PREFERENCE_INSIDE);
        String [] def = p.getStringList(prefix + Properties.NW_PREFERENCE_DEFAULT);

        if (parseNetworkRange(myAddress, range)) {
            logger.info("Network name: " + name + " (MY NETWORK)");
            networkName = name;
            myNetwork = true;
        } else {
            logger.info("Network name: " + name);
        }

        logger.info("  range: " + Arrays.deepToString(range));
        
        logger.info("  in cluster use: " + 
                (network != null ? Arrays.deepToString(network) : ""));
        
        logger.info("  to outside use: " + 
                (def != null ? Arrays.deepToString(def) : ""));

        if (network != null && myNetwork) {
            networksPreference = new Preference("cluster", true);
            handlePreference(networksPreference, network);
        }

        if (def != null && myNetwork) {
            defaultPreference = new Preference("default", false);
            handlePreference(defaultPreference, def);
        }
    }

    private boolean inNetwork(InetSocketAddress[] ads, byte[] sub, byte[] mask) {

        for (int i = 0; i < ads.length; i++) {
            if (NetworkUtils.matchAddress(ads[i].getAddress(), sub, mask)) {
                return true;
            }
        }

        return false;
    }

    private boolean inNetwork(InetAddress[] ads, byte[] sub, byte[] mask) {

        for (int i = 0; i < ads.length; i++) {
            if (NetworkUtils.matchAddress(ads[i], sub, mask)) {
                return true;
            }
        }

        return false;
    }

    private boolean inNetwork(Network [] nw, InetAddress ad) {
        
        for (int i=0;i<nw.length;i++) { 
            if (nw[i].match(ad)) { 
                return true;
            }            
        }
        
        return false;
    }

    private boolean inNetwork(Network [] nw, InetAddress [] ads) {
        
        for (int i=0;i<nw.length;i++) { 
            if (nw[i].match(ads)) { 
                return true;
            }            
        }
        
        return false;
    }
    
    private boolean inNetwork(Network [] nw, InetSocketAddress [] ads) {
        
        for (int i=0;i<nw.length;i++) { 
            if (nw[i].match(ads)) { 
                return true;
            }            
        }
        
        return false;
    }
    
    private boolean inNetwork(InetAddress ad) {
        return (inNetwork(include, ad) && !inNetwork(exclude, ad)); 
    }

    private boolean inNetwork(InetAddress[] ads) {
        return (inNetwork(include, ads) && !inNetwork(exclude, ads)); 
    }

    private boolean inNetwork(InetSocketAddress[] ads) {
        return (inNetwork(include, ads) && !inNetwork(exclude, ads)); 
    }

    private boolean parseNetworkRange(IPAddressSet myAddress, String [] range) {

        ArrayList inc = new ArrayList();
        ArrayList ex = new ArrayList();
        
        for (int i=0;i<range.length;i++) {
            if (range[i].startsWith("!")) {
                ex.add(getNetwork(range[i].substring(1)));
            } else { 
                inc.add(getNetwork(range[i]));
            }            
        }
            
        Network [] include = (Network[]) inc.toArray(new Network[inc.size()]); 
        Network [] exclude = (Network[]) ex.toArray(new Network[ex.size()]); 
        
        InetAddress [] ads = myAddress.getAddresses();
        
        if (inNetwork(include, ads) && !inNetwork(exclude, ads)) { 
            // this seems to be my network!
            this.include = include;
            this.exclude = exclude;
            return true;
        }
        
        return false;
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
            index = range.indexOf('*');
        
            if (index != -1) {
                // IPMASK format                
                range.replaceAll("*", "0");
                sub = addressToBytes(range);                
                mask = new byte[sub.length];                               
                
                for (int i=0;i<sub.length;i++) {
                    if (sub[i] != 0) {
                        mask[i] = (byte) 255;
                    }
                }
            } else { 
                // single address
                sub = addressToBytes(range);                       
                mask = new byte[sub.length];            
                Arrays.fill(mask, (byte) 255);
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

        // Check if the target belongs to our cluster.
        if (networksPreference != null && inNetwork(ads)) { 
            return networksPreference.sort(ads, inPlace);
        }

        return defaultPreference.sort(ads, inPlace);
    }

    public InetAddress[] sort(InetAddress[] ads, boolean inPlace) {

        // Check if the target belongs to our cluster.
        if (networksPreference != null && inNetwork(ads)) { 
            return networksPreference.sort(ads, inPlace);
        }

        return defaultPreference.sort(ads, inPlace);
    }

    public String getClusterName() {
        return networkName;
    }

    public String toString() {
        return defaultPreference.toString();
    }
    
    public static NetworkPreference getPreference(IPAddressSet myAddress, 
            TypedProperties p) { 
        
        return new NetworkPreference(myAddress, p);
    }
}
