package smartsockets.direct;

import ibis.util.TypedProperties;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Properties;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;

import smartsockets.util.NetworkUtils;

public class NetworkPreference {

    private static final String DEFAULT_PREF_FILE = "connection.properties";

    static Logger logger = ibis.util.GetLogger
            .getLogger(NetworkPreference.class.getName());

    private static Properties properties;
    
    private Preference defaultPreference;

    private String clusterName;

    private Preference clusterPreference;

    private byte[] clusterSubnet;

    private byte[] clusterMask;

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
    private NetworkPreference(IPAddressSet myAddress) {

        Properties p = getPropertyFile();

        if (p != null) {
            handleProperties(myAddress, p);
        }

        handleProperties(myAddress, System.getProperties());

        if (defaultPreference == null) {
            logger.info("No default network setup definitions found.");
            defaultPreference = new Preference("default", false);
        }

        logger.info("My cluster is: "
                + (clusterName != null ? clusterName : "N/A"));

        if (clusterPreference == null) {
            logger.info("No cluster definitions found.");
        }
    }

    private static Properties getPropertyFile() {

        String file = TypedProperties
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

    private void handlePreference(Preference target, String property) {

        StringTokenizer tok = new StringTokenizer(property, ",");

        while (tok.hasMoreTokens()) {
            String s = tok.nextToken();

            if (s.equals("site")) {
                // all site local
                target.addSite();
            } else if (s.equals("link")) {
                // all link local
                target.addLink();
            } else if (s.equals("global")) {
                // all global
                target.addGlobal();
            } else {
                // either IP/MASK or IPMASK format
                int index = s.indexOf('/');

                byte[] sub;
                byte[] mask;

                if (index != -1) {
                    // IP/MASK format
                    sub = addressToBytes(s.substring(0, index));
                    mask = addressToBytes(s.substring(index + 1));
                } else {
                    // IPMASK format
                    // TODO: implement
                    sub = mask = null;
                }

                target.addNetwork(sub, mask);
            }
        }
    }

    // This will overwrite any previous preferences.
    private void handleProperties(IPAddressSet myAddress, Properties p) {

        String def = p.getProperty("ibis.connect.preference");

        if (def != null) {
            defaultPreference = new Preference("default", false);
            handlePreference(defaultPreference, def);
        }

        String clusters = p.getProperty("ibis.connect.cluster.define");

        if (clusters == null) {
            return;
        }

        StringTokenizer tok = new StringTokenizer(clusters, ",");

        while (tok.hasMoreTokens()) {
            String name = tok.nextToken();
            handleClusterProperties(myAddress, name, p);
        }
    }

    private void handleClusterProperties(IPAddressSet myAddress, String name,
            Properties p) {

        boolean myCluster = false;

        String prefix = "ibis.connect.cluster." + name;
        String range = p.getProperty(prefix + ".range");
        String cluster = p.getProperty(prefix + ".preference.cluster");
        String def = p.getProperty(prefix + ".preference.default");

        if (inCluster(myAddress, range)) {
            logger.info("Cluster name: " + name + " (MY CLUSTER)");
            clusterName = name;
            myCluster = true;
        } else {
            logger.info("Cluster name: " + name);
        }

        logger.info("  range: " + range);
        logger.info("  in cluster use: " + (cluster != null ? cluster : ""));
        logger.info("  to outside use: " + (def != null ? def : ""));

        if (cluster != null && myCluster) {
            clusterPreference = new Preference("cluster", true);
            handlePreference(clusterPreference, cluster);
        }

        if (def != null && myCluster) {
            defaultPreference = new Preference("default", false);
            handlePreference(defaultPreference, def);
        }
    }

    private boolean inCluster(InetSocketAddress[] ads, byte[] sub, byte[] mask) {

        for (int i = 0; i < ads.length; i++) {
            if (NetworkUtils.matchAddress(ads[i].getAddress(), sub, mask)) {
                return true;
            }
        }

        return false;
    }

    private boolean inCluster(InetAddress[] ads, byte[] sub, byte[] mask) {

        for (int i = 0; i < ads.length; i++) {
            if (NetworkUtils.matchAddress(ads[i], sub, mask)) {
                return true;
            }
        }

        return false;
    }

    private boolean inCluster(IPAddressSet myAddress, String range) {

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

        if (inCluster(myAddress.getAddresses(), sub, mask)) {
            clusterSubnet = sub;
            clusterMask = mask;
            return true;
        } else {
            return false;
        }
    }

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
        if (clusterPreference != null
                && inCluster(ads, clusterSubnet, clusterMask)) {
            return clusterPreference.sort(ads, inPlace);
        }

        return defaultPreference.sort(ads, inPlace);
    }

    public InetAddress[] sort(InetAddress[] ads, boolean inPlace) {

        // Check if the target belongs to our cluster.
        if (clusterPreference != null
                && inCluster(ads, clusterSubnet, clusterMask)) {
            return clusterPreference.sort(ads, inPlace);
        }

        return defaultPreference.sort(ads, inPlace);
    }

    public String getClusterName() {
        return clusterName;
    }

    public String toString() {
        return defaultPreference.toString();
    }
    
    public static NetworkPreference getPreference(IPAddressSet myAddress) { 
        
        if (properties == null) { 
            properties = getPropertyFile();
        }
        
        return new NetworkPreference(myAddress);
    }
}
