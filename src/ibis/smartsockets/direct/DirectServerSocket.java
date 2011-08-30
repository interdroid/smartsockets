package ibis.smartsockets.direct;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.channels.ServerSocketChannel;

/**
 * This class provides a alternative ServerSocket implementation.
 * <p>
 * A DirectServerSocket serves the same purpose as a java.net.ServerSocket,
 * i.e., it acts as a contact point for incoming connections.
 * <p>
 * Unlike a regular ServerSocket, a DirectServerSocket performs a handshake at
 * connection setup to ensure that the connection has reached the correct
 * DirectServerSocket.
 *
 * @author Jason Maassen
 * @version 1.0 Dec 19, 2005
 * @since 1.0
 */
public class DirectServerSocket {

    protected static final byte TYPE_SERVER               = 7;
    protected static final byte TYPE_SERVER_WITH_FIREWALL = 8;
    protected static final byte TYPE_CLIENT_CHECK         = 9;
    protected static final byte TYPE_CLIENT_NOCHECK       = 10;

    protected static final byte ACCEPT = 47;
    protected static final byte WRONG_MACHINE = 48;
    protected static final byte FIREWALL_REFUSED = 49;

    /** The real server socket. */
    private final ServerSocket serverSocket;

    /**
     * This is the local address, i.e., the SocketAddress that uses IP's + ports
     * that are directly found on this machine.
     */
    private final DirectSocketAddress local;

    /**
     * This is the initial message which contains the local address in coded
     * form. The first two bytes contain the size of the address.
     */
    private final byte [] handShake;

    /**
     * This is the initial message which contains the local address in coded
     * form. The first two bytes contain the size of the address.
     */
    private final byte [] altHandShake;

    /**
     * These are the external addresses, i.e., SocketAddresses which use IP's
     * which cannot be found on this machine, but which can still be used to
     * reach this ServerSocket. When a machine is behind a NAT box, for example,
     * it may be reachable using the external IP of the NAT box (when it it
     * using port forwarding). The server socket may also be on the receiving
     * end of an SSH tunnel, reachable thru some proxy, etc.
     * <p>
     * Note that the IP's in the external addresses are not bound to a local
     * network.
     */
    private DirectSocketAddress external;

    /**
     * The network preferences that this server socket should take into account.
     */
    private final NetworkPreference preference;
    private final boolean haveFirewallRules;

    // private long acceptCount = 0;

    protected DirectServerSocket(DirectSocketAddress local, ServerSocket ss,
            NetworkPreference preference) {

        /*super(null);*/
        this.local = local;
        this.serverSocket = ss;
        this.preference = preference;

        byte [] tmp = local.getAddress();

        handShake = new byte[2 + tmp.length];

        handShake[0] = (byte) (tmp.length & 0xFF);
        handShake[1] = (byte) ((tmp.length >> 8) & 0xFF);
        System.arraycopy(tmp, 0, handShake, 2, tmp.length);

        altHandShake = DirectSocketFactory.toBytes(5, local, 2);

        if (preference != null && preference.haveFirewallRules()) {
            haveFirewallRules = true;
            altHandShake[0] = TYPE_SERVER_WITH_FIREWALL;
        } else {
            haveFirewallRules = false;
            altHandShake[0] = TYPE_SERVER;
        }
    }

    /**
     * This method adds an external address to a ServerSocket.
     *
     * External addresses are addresses which can be used to reach the server
     * socket, but which are not bound to local network hardware (i.e., NAT port
     * forwarding, SSH tunnels, etc).
     *
     * @param address the external address to add.
     */
    protected void addExternalAddress(DirectSocketAddress address) {
        // TODO: some checks on the address to see if it makes sence ?

        // Create array if it doesn't exist yet.
        if (external == null) {
            external = address;
        } else {
            external = DirectSocketAddress.merge(external, address);
        }
    }

    private void doClose(Socket s, InputStream in, OutputStream out) {
        try {
            in.close();
        } catch (Exception e) {
            // ignore
        }
        try {
            out.close();
        } catch (Exception e) {
            // ignore
        }

        try {
            s.close();
        } catch (Exception e) {
            // ignore
        }
    }

    /**
     * Accept a new connection.
     *
     * When accepting a connection, a handshake will be performed to
     * ensure that the connection ha reached the intended destination.
     *
     * @return A DirectSocket representing the new connection.
     * @throws IOException
     */
    public DirectSocket accept() throws IOException {

        DirectSimpleSocket result = null;

        // Move up ?
        byte [] userIn = new byte[4];

        while (result == null) {

            // Note: may result in timeout, which is OK.
            Socket s = serverSocket.accept();

           // long t = System.nanoTime();

            InputStream in = null;
            OutputStream out = null;

            // THIS IS THE HPDC VERSION
            try {
                s.setSoTimeout(10000);
                s.setTcpNoDelay(true);

                // Start by sending our type and address to the client. It will
                // check for itself if we are the expected target machine.
                out = s.getOutputStream();
                out.write(altHandShake);
                //out.write(networkNameInBytes);
                out.flush();

                in = s.getInputStream();

                // Read the type of the client (should always be TYPE_CLIENT_*)
                int type = DirectSocketFactory.readByte(in);

                // Read the user data
                DirectSocketFactory.readFully(in, userIn);

                // Read the size of the machines address blob
                int size = (DirectSocketFactory.readByte(in) & 0xFF);
                size |= ((DirectSocketFactory.readByte(in) & 0xFF) << 8);

                // Read the bytes....
                byte [] tmp = DirectSocketFactory.readFully(in, new byte[size]);

                // Read the size of the network name
                size = (DirectSocketFactory.readByte(in) & 0xFF);
                size |= ((DirectSocketFactory.readByte(in) & 0xFF) << 8);

                // Read the name itself....
                byte [] name = DirectSocketFactory.readFully(in, new byte[size]);

                DirectSocketAddress sa = DirectSocketAddress.fromBytes(tmp);

                // Optimistically create the socket ?
                // TODO: fix to get 'real' port numbers here...
                result = new DirectSimpleSocket(local, sa, in, out, s);

                int userData = (((userIn[0] & 0xff) << 24) |
                        ((userIn[1] & 0xff) << 16) |
                        ((userIn[2] & 0xff) << 8) |
                        (userIn[3] & 0xff));

                result.setUserData(userData);

                if (haveFirewallRules) {

                    String network = new String(name);

                    // We must check if we are allowed to accept the client
                    if (preference.accept(sa.getAddressSet().addresses, network)) {
                        out.write(ACCEPT);
                        out.flush();
                    } else {
                        out.write(FIREWALL_REFUSED);
                        out.flush();

                        // TODO: do we really need to wait for incoming byte here ??
                        DirectSocketFactory.readByte(in);
                        doClose(s, in, out);
                        result = null;
                        continue; // TODO: refactor!!!
                    }
                }

                if (type == TYPE_CLIENT_CHECK) {

                    // Read if the client accept us.
                    int opcode = DirectSocketFactory.readByte(in);

                    if (opcode != ACCEPT) {
                        doClose(s, in, out);
                        result = null;
                    }
                }

                s.setSoTimeout(0);

            } catch (IOException ie) {
          /*
                System.err.println("EEK: exception during direct socket handshake!" + ie.getMessage());
                ie.printStackTrace(System.err);
            */
                doClose(s, in, out);
                result = null;
            }

         //   long t2 = System.nanoTime();

      /*      System.err.println("Accept " + acceptCount++ +  " took: "
                    + ((t2-t)/1000) + " usec. "
                    + (result == null ? "(failed)" : "(succes)"));
        */
        }

        return result;
    }

    /**
     * Close the DirectServerSocket.
     *
     * @throws IOException
     */
    public void close() throws IOException {
        serverSocket.close();
    }

    /**
     * Test if the DirectServerSocket is closed.
     *
     * @return if the DirectServerSocket is closed.
     */
    public boolean isClosed() {
        return serverSocket.isClosed();
    }

    /**
     * Get a ServerSocketChannel representing this DirectServerSocket.
     *
     * @return the ServerSocketChannel representing this DirectServerSocket, or
     * null if unavailable.
     * @see java.nio.channels.ServerSocketChannel
     */
    public ServerSocketChannel getChannel() {
        return serverSocket.getChannel();
    }

    /**
     * Get the DirectSocketAddress for this DirectServerSocket.
     *
     * @return the DirectSocketAddress for this DirectServerSocket
     * @see ibis.smartsockets.direct.DirectSocketAddress
     */
    public DirectSocketAddress getAddressSet() {
        if (external != null) {
            return DirectSocketAddress.merge(local, external);
        } else {
            return local;
        }
    }

    public DirectSocketAddress getLocalAddressSet() {
        return local;
    }

    public DirectSocketAddress getExternalAddressSet() {
        return external;
    }

    public synchronized int getSoTimeout() throws IOException {
        return serverSocket.getSoTimeout();
    }

    public synchronized void setSoTimeout(int timeout) throws SocketException {
        serverSocket.setSoTimeout(timeout);
    }

    public boolean getReuseAddress() throws SocketException {
        return serverSocket.getReuseAddress();
    }

    public void setReceiveBufferSize(int size) throws SocketException {
        serverSocket.setReceiveBufferSize(size);
    }

    public void setReuseAddress(boolean on) throws SocketException {
        serverSocket.setReuseAddress(on);
    }

    public String toString() {
        return "DirectServerSocket(" + local + ", " + external + ")";
    }
}
