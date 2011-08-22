package ibis.smartsockets.direct;

import ibis.smartsockets.util.NetworkUtils;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * This class contains the description of a single network.
 *
 * Two types of network descriptions are supported:<p>
 * generic: one of "none", "site", "link", "global"<br>
 * specific: a network address and netmask<br>
 *
 * @author Jason Maassen
 * @version 1.0 Dec 19, 2005
 * @since 1.0
 *
 */
public final class Network {

    private enum Type {
        NONE,
        SITE,
        LINK,
        GLOBAL,
        SPECIFIC;
    }

    /** No network */
    public static final Network NONE = new Network(Type.NONE);

    /** Site local network */
    public static final Network SITE = new Network(Type.SITE);

    /** Link local network */
    public static final Network LINK = new Network(Type.LINK);

    /** Global network */
    public static final Network GLOBAL = new Network(Type.GLOBAL);

    final Type type;
    final byte[] network;
    final byte[] mask;

    Network(Type type) {
        this.type = type;
        this.network = null;
        this.mask = null;
    }

    Network(byte[] network, byte[] mask) {
        this.type = Type.SPECIFIC;
        this.network = network;
        this.mask = mask;
    }

    boolean match(InetAddress addr) {

        switch (type) {
        case NONE:
            return false;
        case SITE:
        case LINK:
            return addr.isSiteLocalAddress();
        case GLOBAL:
            return (!(addr.isSiteLocalAddress() || addr.isLinkLocalAddress() ||
                    addr.isLoopbackAddress() || addr.isAnyLocalAddress() ||
                    addr.isMulticastAddress()));
        case SPECIFIC:
            return NetworkUtils.matchAddress(addr, network, mask);
        }

        // stupid compiler!
        return false;
    }

    boolean match(InetAddress [] addr) {

        for (int i=0;i<addr.length;i++) {
            if (match(addr[i])) {
                return true;
            }
        }

        return false;
    }

    boolean match(InetSocketAddress [] addr) {

        for (int i=0;i<addr.length;i++) {
            if (match(addr[i].getAddress())) {
                return true;
            }
        }

        return false;
    }

    public String toString() {

        switch (type) {
        case NONE:
            return "none";
        case SITE:
            return "site";
        case LINK:
            return "link";
        case GLOBAL:
            return "global";
        case SPECIFIC:
            return NetworkUtils.bytesToString(network) + "/"
                + NetworkUtils.bytesToString(mask);
        }

        // Stupid compiler!
        return "";
    }
}