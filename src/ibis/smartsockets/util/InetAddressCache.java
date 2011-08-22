package ibis.smartsockets.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InetAddressCache {

    /** How long entries in the cache stay valid, in milliseconds. */
    public static final long ENTRY_TIMEOUT = 300000;

    private static class Address {
        InetAddress address;
        long        creationTime;

        public Address(InetAddress address, long creationTime) {
            this.address = address;
            this.creationTime = creationTime;
        }
    }

    private static Logger logger =
        LoggerFactory.getLogger(InetAddressCache.class.getName());

    private static Map<String, Address> cache = new HashMap<String, Address>();

    private InetAddressCache() {
        // prevent construction
    }

    public static synchronized InetAddress getByName(String name)
            throws UnknownHostException {
        Address result = null;
        long time = System.currentTimeMillis();
        if (cache.containsKey(name)) {
            result = cache.get(name);
            if (time - result.creationTime < ENTRY_TIMEOUT) {
                if (result.address == null) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("getByName had an unsuccessful cache hit on " + name);
                    }
                    throw new UnknownHostException("Could not find host " + name);
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("getByName had a cache hit on " + name);
                }
                return result.address;
            } else {
                cache.remove(name);
            }
        }

        if (logger.isDebugEnabled()) {
            logger.debug("getByName had a cache miss on " + name);
        }

        try {
            result = new Address(InetAddress.getByName(name), time);
        } catch (UnknownHostException e) {
            cache.put(name, new Address(null, time));
            throw e;
        }
        cache.put(name, result);
        return result.address;
    }
}
