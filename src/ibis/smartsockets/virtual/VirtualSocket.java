package ibis.smartsockets.virtual;


import ibis.smartsockets.direct.DirectSocketAddress;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.SocketChannel;
import java.util.Map;

/**
 * This is the base class for various Virtual Socket implementations.
 *
 *
 */
public abstract class VirtualSocket {

    /** The remote address connected to. */
    protected DirectSocketAddress remote;

    /** The remote port connected to. */
    protected int remotePort;

    /** The configuration of this VirtualSocket. */
    protected Map<String, Object> props;

    /**
     * Create an unbound VirtualSocket.
     */
    protected VirtualSocket() {
    }

    /**
     * Create a VirtualSocket connected to a remote address.
     * @param target the address to bind to.
     */
    protected VirtualSocket(VirtualSocketAddress target) {
        this.remote = target.machine();
        this.remotePort = target.port();
    }

    /**
     * Returns the address of the remote endpoint this socket is connected to,
     * or null if it is unconnected.
     *
     * @return the address of the remote endpoint.
     */
    public SocketAddress getRemoteSocketAddress() {
        return remote;
    }

    /**
     * Returns the remote port to which this socket is connected.
     *
     * @return the remote port number to which this socket is connected, or 0
     * if the socket is not connected yet.
     */
    public int getPort() {
        return remotePort;
    }

    /**
     * Returns the number of bytes written to this VirtualSocket.
     *
     * @return the number of bytes written.
     * @throws IOException if the number of bytes written cannot be retrieved.
     */
    public long getBytesWritten() throws IOException {
        System.err.println("getBytesWritten() not implemented by " + this);
        throw new RuntimeException("getBytesWritten() not implemented by "
                                  + this);
    }

    /**
     * Returns the number of bytes read from this VirtualSocket.
     *
     * @return the number of bytes read.
     * @throws IOException if the number of bytes read cannot be retrieved.
     */
    public long getBytesRead() throws IOException {
        System.err.println("getBytesRead() not implemented by " + this);
        throw new RuntimeException("getBytesRead() not implemented by " + this);
    }

    /**
     * Closes this socket.
     *
     * Once a socket has been closed, it is not available for further networking
     * use (i.e. can't be reconnected or rebound). A new socket needs to be
     * created.
     * <p>
     * Closing this socket will also close the socket's InputStream and
     * OutputStream.
     * <p>
     * If this socket has an associated channel then the channel is closed as
     * well.
     *
     * @throws IOException if an I/O error occurs when closing this socket.
     */
    public void close() throws IOException {
        System.err.println("close() not implemented by " + this);
        throw new RuntimeException("close() not implemented by " + this);
    }

    /**
     * Returns the unique SocketChannel object associated with this socket, if
     * any.
     *
     * @return the socket channel associated with this socket, or null if it is
     * not available.
     */
    public SocketChannel getChannel() {
        System.err.println("getChannel() not implemented by " + this);
        throw new RuntimeException("getChannel() not implemented by " + this);
    }

    /**
     * Returns an input stream for this socket.
     *
     * @return an input stream for reading bytes from this socket.
     * @throws IOException if an I/O error occurs when creating the input
     * stream,the socket is closed or the socket is not connected.
     */
    public InputStream getInputStream() throws IOException {
        System.err.println("getInputStream() not implemented by " + this);
        throw new RuntimeException("getInputStream() not implemented by "
                + this);
    }

    /**
     * Tests if SO_KEEPALIVE is enabled.
     *
     * @return if SO_KEEPALIVE is enabled.
     * @throws SocketException the test count not be performed.
     */
    public boolean getKeepAlive() throws SocketException {
        System.err.println("getKeepAlive() not implemented by " + this);
        throw new RuntimeException("getKeepAlive() not implemented by " + this);
    }

    /**
     * Returns the local port to which this socket is bound.
     *
     * @return the local port number to which this socket is bound or -1 if
     * the socket is not bound yet.
     */
    public int getLocalPort() {
        System.err.println("getLocalPort() not implemented by " + this);
        throw new RuntimeException("getLocalPort() not implemented by " + this);
    }

    /**
     * Returns the address which this socket is bound to, or null if it is not
     * bound yet.
     *
     * @return SocketAddress representing the local endpoint of this socket, or
     * null if it is not bound yet.
     */
    public SocketAddress getLocalSocketAddress() {
        System.err.println("getLocalSocketAddress() not implemented by "+ this);
        throw new RuntimeException(
                "getLocalSocketAddress() not implemented by " + this);
    }

    /**
     * Tests if OOBINLINE is enabled.
     *
     * @return if OOBINLINE is enabled.
     * @throws SocketException if the test failed.
     */
    public boolean getOOBInline() throws SocketException {
        System.err.println("getOOBInline() not implemented by " + this);
        throw new RuntimeException("getOOBInline() not implemented by " + this);
    }

    /**
     * Returns an output stream for this socket.
     *
     * @return an output stream for writing bytes to this socket.
     * @throws IOException if an I/O error occurs when creating the output
     * stream or if the socket is not connected.
     */
    public OutputStream getOutputStream() throws IOException {
        System.err.println("getOutputStream() not implemented by " + this);
        throw new RuntimeException("getOutputStream() not implemented by "
                + this);
    }

    /**
     * Gets the value of the SO_RCVBUF option for this Socket (the receive
     * buffer size).
     *
     * @return the value of the SO_RCVBUF option for this Socket.
     * @throws SocketException if there is an error in retrieving SO_RCVBUF.
     */
    public int getReceiveBufferSize() throws SocketException {
        System.err.println("getReceiveBufferSize() not implemented by " + this);
        throw new RuntimeException("getReceiveBufferSize() not implemented by "
                + this);
    }

    /**
     * Tests if SO_REUSEADDR is enabled.
     *
     * @return if SO_REUSEADDR is enabled.
     * @throws SocketException if the test failed.
     */
    public boolean getReuseAddress() throws SocketException {
        System.err.println("getReuseAddress() not implemented by " + this);
        throw new RuntimeException("getReuseAddress() not implemented by "
                + this);
    }

    /**
     * Gets the value of the SO_SNDBUF option for this Socket (the send
     * buffer size).
     *
     * @return the value of the SO_SNDBUF option for this Socket.
     * @throws SocketException if there is an error in retrieving SO_SNDBUF.
     */
    public int getSendBufferSize() throws SocketException {
        System.err.println("getSendBufferSize() not implemented by " + this);
        throw new RuntimeException("getSendBufferSize() not implemented by "
                + this);
    }

    /**
     * Returns setting for SO_LINGER. -1 returns implies that the option is
     * disabled. The setting only affects socket close.
     *
     * @return the setting for SO_LINGER.
     * @throws SocketException if there is an error in retrieving SO_LINGER.
     */
    public int getSoLinger() throws SocketException {
        System.err.println("getSoLinger() not implemented by " + this);
        throw new RuntimeException("getSoLinger() not implemented by " + this);
    }

    /**
     * Returns setting for SO_TIMEOUT. 0 returns implies that the option is
     * disabled (i.e., timeout of infinity).
     *
     * @return the value of SO_TIMEOUT.
     * @throws SocketException if there is an error in retrieving SO_TIMEOUT.
     */
    public int getSoTimeout() throws SocketException {
        System.err.println("getSoTimeout() not implemented by " + this);
        throw new RuntimeException("getSoTimeout() not implemented by " + this);
    }

    /**
     * Tests if TCP_NODELAY is enabled.
     *
     * @return if TCP_NODELAY is enabled.
     * @throws SocketException if there is an error in retrieving TCP_NODELAY.
     */
    public boolean getTcpNoDelay() throws SocketException {
        System.err.println("getTcpNoDelay() not implemented by " + this);
        throw new RuntimeException("getTcpNoDelay() not implemented by "+ this);
    }

    /**
     * Gets traffic class for packets sent from this Socket.
     *
     * @return the traffic class set.
     * @throws SocketException  if there is an error in retrieving the traffic
     * class.
     */
    public int getTrafficClass() throws SocketException {
        System.err.println("getTrafficClass() not implemented by " + this);
        throw new RuntimeException("getTrafficClass() not implemented by "
                + this);
    }

    /**
     * Returns if this socket is bound.
     *
     * @return if the socket is bound.
     */
    public boolean isBound() {
        System.err.println("isBound() not implemented by " + this);
        throw new RuntimeException("isBound() not implemented by " + this);
    }

    /**
     * Returns if this socket is closed.
     *
     * @return if the socket is closed.
     */
    public boolean isClosed() {
        System.err.println("isClosed() not implemented by " + this);
        throw new RuntimeException("isClosed() not implemented by " + this);
    }

    /**
     * Returns if this socket is connected.
     *
     * @return if the socket is connected.
     */
    public boolean isConnected() {
        System.err.println("isConnected() not implemented by " + this);
        throw new RuntimeException("isConnected() not implemented by " + this);
    }

    /**
     * Returns if the input of this socket is shut down.
     *
     * @return if the input of this socket is shut down.
     */
    public boolean isInputShutdown() {
        System.err.println("isInputShutdown() not implemented by " + this);
        throw new RuntimeException("isInputShutdown() not implemented by "
                + this);
    }

    /**
     * Returns if the output of this socket is shut down.
     *
     * @return if the output of this socket is shut down.
     */
    public boolean isOutputShutdown() {
        System.err.println("isOutputShutdown() not implemented by " + this);
        throw new RuntimeException("isOutputShutdown() not implemented by "
                + this);
    }

    /**
     * Send one byte of urgent data on the socket.
     * @param data the byte to send (only the lowest 8 bits are used).
     * @throws IOException if there is an error in sending the data.
     */
    public void sendUrgentData(int data) throws IOException {
        System.err.println("sendUrgentData(int) not implemented by " + this);
        throw new RuntimeException("sendUrgentData(int) not implemented by "
                + this);
    }

    /**
     * Enable/disable SO_KEEPALIVE.
     *
     * @param on should SO_KEEPALIVE be turned on ?
     * @throws SocketException if an error occurred setting SO_KEEPALIVE.
     */
    public void setKeepAlive(boolean on) throws SocketException {
        System.err.println("setKeepAlive(boolean) not implemented by " + this);
        throw new RuntimeException("setKeepAlive(boolean) not implemented by "
                + this);
    }

    /**
     * Enable/disable SO_OOBINLINE.
     *
     * @param on should SO_OOBINLINE be turned on ?
     * @throws SocketException if an error occurred setting SO_OOBINLINE.
     */
    public void setOOBInline(boolean on) throws SocketException {
        System.err.println("setOOBInline(boolean) not implemented by " + this);
        throw new RuntimeException("setOOBInline(boolean) not implemented by "
                + this);
    }

    /**
     * Set the value of SO_RCVBUF (the receive buffer size). This is only a hint
     * to the underlying implementation, and the value may be ignored or
     * altered.
     *
     * @param sz the suggested size of SO_RCVBUF.
     * @throws SocketException if an error occurred setting SO_RCVBUF.
     */
    public void setReceiveBufferSize(int sz) throws SocketException {
        System.err.println("setReceiveBufferSize(int) not implemented by "
                + this);
        throw new RuntimeException(
                "setReceiveBufferSize(int) not implemented by " + this);
    }

    /**
     * Enable/disable SO_REUSEADDR.
     *
     * @param on should SO_REUSEADDR be turned on ?
     * @throws SocketException if an error occurred setting SO_REUSEADDR.
     */
    public void setReuseAddress(boolean on) throws SocketException {
        System.err.println("setReuseAddress(boolean) not implemented by "
                + this);
        throw new RuntimeException(
                "setReuseAddress(boolean) not implemented by " + this);
    }

    /**
     * Set the value of SO_SNDBUF (the send buffer size). This is only a hint
     * to the underlying implementation, and the value may be ignored or
     * altered.
     *
     * @param sz the suggested size of SO_SNDBUF.
     * @throws SocketException if an error occurred setting SO_SNDBUF.
     */
    public void setSendBufferSize(int sz) throws SocketException {
        System.err.println("setSendBufferSize(int) not implemented by " + this);
        throw new RuntimeException("setSendBufferSize(int) not implemented by "
                + this);
    }

    /**
     * Enable/disable SO_LINGER with the specified linger time in seconds.
     *
     * @param on should SO_LINGER be turned on ?
     * @param linger linger time in seconds.
     * @throws SocketException if an error occurred setting SO_LINGER.
     */
    public void setSoLinger(boolean on, int linger) throws SocketException {
        System.err.println("setSoLinger(boolean, int) not implemented by "
                + this);
        throw new RuntimeException(
                "setSoLinger(boolean, int) not implemented by " + this);
    }

    /**
     * Set SO_TIMEOUT with the specified timeout, in milliseconds.
     * A value of zero is interpreted as an infinite timeout. Negative values
     * are not allowed.
     *
     * @param t the timeout, in milliseconds.
     * @throws SocketException if an error occurred setting SO_TIMEOUT.
     */
    public void setSoTimeout(int t) throws SocketException {
        System.err.println("setSoTimeout(int) not implemented by " + this);
        throw new RuntimeException("setSoTimeout(int) not implemented by "
                + this);
    }

    /**
     * Enable/disable TCP_NODELAY.
     *
     * @param on should TCP_NODELAY be turned on ?
     * @throws SocketException if an error occurred setting TCP_NODELAY.
     */
    public void setTcpNoDelay(boolean on) throws SocketException {
        System.err.println("setTcpNoDelay(boolean) not implemented by " + this);
        throw new RuntimeException("setTcpNoDelay(boolean) not implemented by "
                + this);
    }

    /**
     * Set traffic class of the IP packets.
     *
     * @param tc the traffic class.
     * @throws SocketException if an error occurred setting TCP_NODELAY.
     */
    public void setTrafficClass(int tc) throws SocketException {
        System.err.println("setTrafficClass(int) not implemented by " + this);
        throw new RuntimeException("setTrafficClass(int) not implemented by "
                + this);
    }

    /**
     * Shutdown the input of the Socket by placing it at end-of-stream.
     *
     * Any further data received will be discarded.
     *
     * @throws IOException if an I/O error occurs when shutting down the input.
     */
    public void shutdownInput() throws IOException {
        System.err.println("shutdownInput() not implemented by " + this);
        throw new RuntimeException("shutdownInput() not implemented by "+ this);
    }

    /**
     * Flush any written data and shutdown the output of the Socket.
     *
     * Any further data writes on the socket will result in an IOException.
     *
     * @throws IOException if an I/O error occurs when shutting down the output.
     */
    public void shutdownOutput() throws IOException {
        System.err.println("shutdownOutput() not implemented by " + this);
        throw new RuntimeException("shutdownOutput() not implemented by "
                + this);
    }

    /**
     * Sets performance preferences for this socket.
     *
     * @param connectionTime relative importance of a short connection time.
     * @param latency relative importance of a short latency.
     * @param bandwidth relative importance of a high bandwidth.
     */
    public void setPerformancePreferences(int connectionTime, int latency,
            int bandwidth) {
        System.err.println("setPerformancePreferences() not implemented by "
                + this);

        throw new RuntimeException("shutdownOutput() not implemented by "
                + this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "unimplemented toString()!";
    }

    /**
     * Returns the properties associated with this socket.
     *
     * @return a map containing implementation-specific properties of this
     *         socket.
     */
    public Map<String, Object> properties() {
        return props;
    }

    /**
     * Sets a number of properties of this socket.
     *
     * Allows the user to set implementation-specific properties of this socket.
     *
     * @param properties map containing the properties to set.
     *
     */
    public void setProperties(Map<String, Object> properties) {
        // TODO: Add instead of overwrite ?
        props = properties;
    }

    /**
     * Returns the value associated with a specific property of this socket.
     *
     * @param key name of the property whose value should be returned
     * @return value associated with the property, or null if the property does
     *         not exist.
     */
    public Object getProperty(String key) {
        return props.get(key);
    }

    /**
     * Sets a property of this socket.
     *
     * @param key name of the property to set.
     * @param val new value of the property.
     */
    public void setProperty(String key, Object val) {
        props.put(key, val);
    }

    /**
     * Wait for a specified timeout for the target to accept a connection from
     * this socket.
     *
     * @param timeout the timeout in milliseconds.
     * @throws IOException if the connection failed to be created.
     */
    public abstract void waitForAccept(int timeout) throws IOException;

    /**
     * Accept this connection.
     *
     * @param timeout the time allocated for the accept.
     * @throws IOException the accept failed.
     */
    protected abstract void connectionAccepted(int timeout) throws IOException;

    /**
     * Reject this connection.
     *
     * @param timeout the time allocated for the reject.
     */
    protected abstract void connectionRejected(int timeout);

    /**
     * DO NOT USE!
     *
     * @return always null
     */
    public InetAddress getLocalAddress() {
        // NOTE: Can never be implemented correctly ?
        return null;
    }

    /**
     * DO NOT USE!
     *
     * @return always null
     */
    public InetAddress getInetAddress() {
        // NOTE: Can never be implemented correctly ?
        return null;
    }



}
