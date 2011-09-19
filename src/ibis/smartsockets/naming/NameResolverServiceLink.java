package ibis.smartsockets.naming;

import ibis.smartsockets.direct.DirectSocketAddress;
import ibis.smartsockets.hub.servicelink.CallBack;
import ibis.smartsockets.hub.servicelink.ClientInfo;
import ibis.smartsockets.hub.servicelink.ServiceLink;
import ibis.smartsockets.util.MalformedAddressException;
import ibis.smartsockets.util.ThreadPool;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Uses the service link to handle naming. This is a rather poor
 * implementation because the service link doesn't have a broadcast
 * primitive which is what we really want and also because we assume
 * that we want to collect all names locally.
 *
 * @author nick <palmer@cs.vu.nl>
 *
 */
public class NameResolverServiceLink extends NameResolverMethod {
    /** The logger we use. **/
    private static final Logger LOGGER = LoggerFactory
            .getLogger("ibis.smartsockets.naming.sl");

    private static final String NAMING_MODULE = "naming";

    /**
     * The opcode used to send an add message.
     */
    private static final int NAME_ADD = 1;
    /**
     * The opcode used to send a remove message.
     */
    private static final int NAME_REMOVE = 2;

    /**
     * The opcode used to request that a node send us their names
     */
    private static final int NAME_QUERY = 3;

    protected static final int TIMEOUT = 0;

    // Register us with the methods
    static {
        NameResolverMethod.registerMethod(NameResolverServiceLink.class);
    }

    /**
     * The service link we communicate over
     */
    private ServiceLink mServiceLink;

    /**
     * The callback which gets messages about naming events
     */
    private CallBack mCallback;

    /* =-=-=- Private Helper Methods =-=-=- */

    /**
     * Constructs an add message from the given data.
     * @param name the name of the socket
     * @param address the address of the socket
     * @param info the application specific info for this socket
     * @return the message buffer
     */
    private byte[][] buildAddMessage(String name, String address,
            Map<String, String> info) {
        byte[][] message = new byte[3 + (info == null ? 0 : (2 * info.size()))][];

        message[0] = name.getBytes();
        message[1] = address.getBytes();
        if (info != null) {
            message[2] = fromInt(info.size());
            int offset = 3;
            for (Entry<String, String> entry : info.entrySet()) {
                message[offset++] = entry.getKey().getBytes();
                message[offset++] = entry.getValue().getBytes();
            }
        } else {
            message[2] = fromInt(0);
        }

        return message;
    }

    /**
     * Constructs a remove message from the given data.
     * @param name the name of the socket
     * @return the message buffer
     */
    private byte[][] buildNameMessage(String name) {
        byte[][] message = new byte[1][];

        message[0] = name.getBytes();

        return message;
    }

    /**
     * Converts a byte array to an integer
     * @param m the byte array to convert from
     * @return the value in the byte array as an integer
     */
    private int toInt(byte [] m) {
        return (((m[0] & 0xff) << 24) |
                ((m[1] & 0xff) << 16) |
                ((m[2] & 0xff) << 8) |
                (m[3] & 0xff));
    }

    /**
     * Converts from an integer to a byte array
     * @param v the integer to be converted
     * @return a byte array with the value of the integer
     */
    private byte [] fromInt(int v) {
        return new byte[] {
                (byte)(0xff & (v >> 24)),
                (byte)(0xff & (v >> 16)),
                (byte)(0xff & (v >> 8)),
                (byte)(0xff & v) };
    }

    /**
     * Initializes the callback handler we use to send messages
     */
    private void initializeCallback() {
        mCallback = new CallBack() {

            @Override
            public void gotMessage(DirectSocketAddress src,
                    DirectSocketAddress srcProxy, int opcode,
                    boolean returnToSender, byte[][] message) {
                switch (opcode) {
                case NAME_ADD:
                    handleAddMessage(message);
                    break;
                case NAME_QUERY:
                    handleQueryMessage(src, srcProxy, message);
                    break;
                case NAME_REMOVE:
                    handleRemoveMessage(message);
                    break;
                default:
                    LOGGER.error("Unknown opcode: {}", opcode);
                }
            }

        };
    }

    private void handleQueryMessage(DirectSocketAddress src,
            DirectSocketAddress srcProxy, byte[][] message) {
        String name = new String(message[0]);
        LOGGER.info("Got query for name: {}", name);
        if (getResolver().isAuthorityFor(name)) {
            LOGGER.info("I am authority for that name. Answering.");
            String address =
                    getResolver().resolve(name,
                            TIMEOUT).toString();
            if (address != null) {
                LOGGER.info("Sending reply: {} {}", name, address);
                Map<String, String> info =
                        getResolver().resolveInfo(name, TIMEOUT);
                mServiceLink.send(src, srcProxy, NAMING_MODULE,
                        NAME_ADD,
                        buildAddMessage(name, address, info));
            }
        } else {
            LOGGER.info("Not authority for that name.");
        }
    }

    /**
     * Handle a remove message coming in over the service link
     * @param message the message with the name
     */
    private void handleRemoveMessage(byte[][] message) {
        String name = new String(message[0]);
        LOGGER.debug("Got remove: {}", name);
        getResolver().handleRemove(false, name);
    }

    /**
     * Handle an add message coming in over theservice link
     * @param message the message with the address information
     */
    private void handleAddMessage(byte[][] message) {
        String name = new String(message[0]);
        String address = new String(message[1]);
        LOGGER.debug("Got add: {} {}", name, address);
        int size = toInt(message[2]);
        Map<String, String>info = new HashMap<String, String>();
        int offset = 3;
        for (int i = 0; i < size; i++) {
            info.put(String.valueOf(message[offset]),
                    String.valueOf(message[offset + 1]));
            offset += 2;
        }
        try {
            getResolver().handleAdd(false, name, address, info);
        } catch (UnknownHostException e) {
            LOGGER.error("Unknown host while adding.", e);
        } catch (MalformedAddressException e) {
            LOGGER.error("Malformed address while adding.", e);
        }
    }

    /* =-=-=- Overrides from super class =-=-=- */

    @Override
    public void register(String name, String address, Map<String, String> info)
            throws IOException {
        LOGGER.info("Registering: {} {}", name, address);
        // TODO: Service link should have a broadcast primitive
        // Look through all hubs
        for (DirectSocketAddress hub : mServiceLink.hubs()) {
            // And look at all of their clients
            for (ClientInfo client : mServiceLink.clients(hub)) {
                // Send an add message to each client
                mServiceLink.send(client.getClientAddress(), hub,
                        NAMING_MODULE, NAME_ADD,
                        buildAddMessage(name, address, info));
            }
        }
    }


    @Override
    public void unregister(String name) throws IOException {
        LOGGER.info("Unregistering: {}", name);
        // TODO: Service link should have a broadcast primitive
        // Look through all hubs
        for (DirectSocketAddress hub : mServiceLink.hubs()) {
            // And look at all of their clients
            for (ClientInfo client : mServiceLink.clients(hub)) {
                // Send a remove message to each client
                mServiceLink.send(client.getClientAddress(), hub,
                        NAMING_MODULE, NAME_REMOVE,
                        buildNameMessage(name));
            }
        }
    }

    @Override
    public void start() throws IOException {
        mServiceLink = getResolver().getSocketFactory().getServiceLink();
        if (mServiceLink != null) {
            initializeCallback();
            mServiceLink.registerProperty("smartsockets.viz", "N^naming^naming service^" + 0xff0000ff);
            mServiceLink.registerProperty("naming", "true");
            mServiceLink.register("naming", mCallback);
        } else {
            LOGGER.error("No service link for naming.");
            throw new IOException("No service link to use.");
        }
        try {
            synchronized (this) {
                // Wait two hub gossip period to find hubs and clients
                // This ensures that we have information if we receive
                // a query just after they requested we start.
                this.wait(6000);
            }
        } catch (InterruptedException e) {
            // Ignored.
        }
        LOGGER.info("Service Link Naming started.");
    }

    @Override
    public void stop() {
        // Nothing to do since ServiceLink can't remove a module.
    }

    @Override
    public void query(final String name) {
        ThreadPool.createNew(new Runnable() {
            @Override
            public void run() {
                try {
                    // TODO: Service link should have a broadcast primitive
                    final DirectSocketAddress[] hubs = mServiceLink.hubs();
                    LOGGER.debug("Have: {} hubs", hubs.length);
                    for (final DirectSocketAddress hub : hubs) {
                        ThreadPool.createNew(new Runnable() {

                            @Override
                            public void run() {
                                LOGGER.debug("Checking hub: {}", hub);
                                // And look at all of their clients
                                try {
                                    final ClientInfo[] clients = mServiceLink.clients(hub);
                                    LOGGER.debug("Hub has {} clients.", clients.length);
                                    for (final ClientInfo client : clients) {
                                        // Send a query message to each naming client
                                        LOGGER.debug("Checking client: {}", client.getClientAddress());
                                        if (client.hasProperty("naming")) {
                                            ThreadPool.createNew(new Runnable() {

                                                @Override
                                                public void run() {
                                                    LOGGER.info("Querying for: {} to: {}", name, client.getClientAddress());
                                                    mServiceLink.send(client.getClientAddress(), null,
                                                            NAMING_MODULE, NAME_QUERY, buildNameMessage(name));
                                                    LOGGER.debug("Query sent.");
                                                }

                                            },
                                            "query-naming-client: " + name +
                                            " " + client.getClientAddress());
                                        } else {
                                            LOGGER.debug("Not a naming client.");
                                        }
                                    }
                                } catch (Exception e) {
                                    LOGGER.error("Error asking for clients", e);
                                }
                            }

                        }, "query-service-link: " +
                                name + " : " + hub);
                    }
                } catch (Exception e) {
                    LOGGER.error("Error while querying for name", e);
                }
            }
        }, "query-service-link: " + name);
    }

}
