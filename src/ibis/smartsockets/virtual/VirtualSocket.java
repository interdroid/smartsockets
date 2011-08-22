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


public abstract class VirtualSocket {

    protected DirectSocketAddress remote;
    protected int remotePort;

    protected Map<String, Object> props;

    protected VirtualSocket() {
    }

    protected VirtualSocket(VirtualSocketAddress target) {
        this.remote = target.machine();
        this.remotePort = target.port();
    }

    public SocketAddress getRemoteSocketAddress() {
        return remote;
    }

    public int getPort() {
        return remotePort;
    }

    public long getBytesWritten() throws IOException {
        System.err.println("getBytesWritten() not implemented by " + this);
        throw new RuntimeException("getBytesWritten() not implemented by " + this);
    }

    public long getBytesRead() throws IOException {
        System.err.println("getBytesRead() not implemented by " + this);
        throw new RuntimeException("getBytesRead() not implemented by " + this);
    }

    public void close() throws IOException {
        System.err.println("close() not implemented by " + this);
        throw new RuntimeException("close() not implemented by " + this);
    }

    public SocketChannel getChannel() {
        System.err.println("getChannel() not implemented by " + this);
        throw new RuntimeException("getChannel() not implemented by " + this);
    }

    public InputStream getInputStream() throws IOException {
        System.err.println("getInputStream() not implemented by " + this);
        throw new RuntimeException("getInputStream() not implemented by "
                + this);
    }

    public boolean getKeepAlive() throws SocketException {
        System.err.println("getKeepAlive() not implemented by " + this);
        throw new RuntimeException("getKeepAlive() not implemented by " + this);
    }

    public int getLocalPort() {
        System.err.println("getLocalPort() not implemented by " + this);
        throw new RuntimeException("getLocalPort() not implemented by " + this);
    }

    public SocketAddress getLocalSocketAddress() {
        System.err
                .println("getLocalSocketAddress() not implemented by " + this);
        throw new RuntimeException(
                "getLocalSocketAddress() not implemented by " + this);
    }

    public boolean getOOBInline() throws SocketException {
        System.err.println("getOOBInline() not implemented by " + this);
        throw new RuntimeException("getOOBInline() not implemented by " + this);
    }

    public OutputStream getOutputStream() throws IOException {
        System.err.println("getOutputStream() not implemented by " + this);
        throw new RuntimeException("getOutputStream() not implemented by "
                + this);
    }

    public int getReceiveBufferSize() throws SocketException {
        System.err.println("getReceiveBufferSize() not implemented by " + this);
        throw new RuntimeException("getReceiveBufferSize() not implemented by "
                + this);
    }

    public boolean getReuseAddress() throws SocketException {
        System.err.println("getReuseAddress() not implemented by " + this);
        throw new RuntimeException("getReuseAddress() not implemented by "
                + this);
    }

    public int getSendBufferSize() throws SocketException {
        System.err.println("getSendBufferSize() not implemented by " + this);
        throw new RuntimeException("getSendBufferSize() not implemented by "
                + this);
    }

    public int getSoLinger() throws SocketException {
        System.err.println("getSoLinger() not implemented by " + this);
        throw new RuntimeException("getSoLinger() not implemented by " + this);
    }

    public int getSoTimeout() throws SocketException {
        System.err.println("getSoTimeout() not implemented by " + this);
        throw new RuntimeException("getSoTimeout() not implemented by " + this);
    }

    public boolean getTcpNoDelay() throws SocketException {
        System.err.println("getTcpNoDelay() not implemented by " + this);
        throw new RuntimeException("getTcpNoDelay() not implemented by " + this);
    }

    public int getTrafficClass() throws SocketException {
        System.err.println("getTrafficClass() not implemented by " + this);
        throw new RuntimeException("getTrafficClass() not implemented by "
                + this);
    }

    public boolean isBound() {
        System.err.println("isBound() not implemented by " + this);
        throw new RuntimeException("isBound() not implemented by " + this);
    }

    public boolean isClosed() {
        System.err.println("isClosed() not implemented by " + this);
        throw new RuntimeException("isClosed() not implemented by " + this);
    }

    public boolean isConnected() {
        System.err.println("isConnected() not implemented by " + this);
        throw new RuntimeException("isConnected() not implemented by " + this);
    }

    public boolean isInputShutdown() {
        System.err.println("isInputShutdown() not implemented by " + this);
        throw new RuntimeException("isInputShutdown() not implemented by "
                + this);
    }

    public boolean isOutputShutdown() {
        System.err.println("isOutputShutdown() not implemented by " + this);
        throw new RuntimeException("isOutputShutdown() not implemented by "
                + this);
    }

    public void sendUrgentData(int data) throws IOException {
        System.err.println("sendUrgentData(int) not implemented by " + this);
        throw new RuntimeException("sendUrgentData(int) not implemented by "
                + this);
    }

    public void setKeepAlive(boolean on) throws SocketException {
        System.err.println("setKeepAlive(boolean) not implemented by " + this);
        throw new RuntimeException("setKeepAlive(boolean) not implemented by "
                + this);
    }

    public void setOOBInline(boolean on) throws SocketException {
        System.err.println("setOOBInline(boolean) not implemented by " + this);
        throw new RuntimeException("setOOBInline(boolean) not implemented by "
                + this);
    }

    public void setReceiveBufferSize(int sz) throws SocketException {
        System.err.println("setReceiveBufferSize(int) not implemented by "
                + this);
        throw new RuntimeException(
                "setReceiveBufferSize(int) not implemented by " + this);
    }

    public void setReuseAddress(boolean on) throws SocketException {
        System.err.println("setReuseAddress(boolean) not implemented by "
                + this);
        throw new RuntimeException(
                "setReuseAddress(boolean) not implemented by " + this);
    }

    public void setSendBufferSize(int sz) throws SocketException {
        System.err.println("setSendBufferSize(int) not implemented by " + this);
        throw new RuntimeException("setSendBufferSize(int) not implemented by "
                + this);
    }

    public void setSoLinger(boolean on, int linger) throws SocketException {
        System.err.println("setSoLinger(boolean, int) not implemented by "
                + this);
        throw new RuntimeException(
                "setSoLinger(boolean, int) not implemented by " + this);
    }

    public void setSoTimeout(int t) throws SocketException {
        System.err.println("setSoTimeout(int) not implemented by " + this);
        throw new RuntimeException("setSoTimeout(int) not implemented by "
                + this);
    }

    public void setTcpNoDelay(boolean on) throws SocketException {
        System.err.println("setTcpNoDelay(boolean) not implemented by " + this);
        throw new RuntimeException("setTcpNoDelay(boolean) not implemented by "
                + this);
    }

    public void setTrafficClass(int tc) throws SocketException {
        System.err.println("setTrafficClass(int) not implemented by " + this);
        throw new RuntimeException("setTrafficClass(int) not implemented by "
                + this);
    }

    public void shutdownInput() throws IOException {
        System.err.println("shutdownInput() not implemented by " + this);
        throw new RuntimeException("shutdownInput() not implemented by " + this);
    }

    public void shutdownOutput() throws IOException {
        System.err.println("shutdownOutput() not implemented by " + this);
        throw new RuntimeException("shutdownOutput() not implemented by "
                + this);
    }

    public void setPerformancePreferences(int connectionTime, int latency,
            int bandwidth) {
        System.err.println("setPerformancePreferences() not implemented by "
                + this);

        throw new RuntimeException("shutdownOutput() not implemented by "
                + this);
    }

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
     *
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
     * Configures a socket according to user-specified properties. Currently,
     * the input buffer size and output buffer size can be set using the system
     * properties "ibis.util.socketfactory.InputBufferSize" and
     * "ibis.util.socketfactory.OutputBufferSize".
     *
     * @exception IOException configuration failed for some reason.
     */

    public abstract void waitForAccept(int timeout) throws IOException;
    protected abstract void connectionAccepted(int timeout) throws IOException;
    protected abstract void connectionRejected(int timeout);

    public InetAddress getLocalAddress() {
        // NOTE: Can never be implemented correctly ?
        return null;
    }

    public InetAddress getInetAddress() {
        // NOTE: Can never be implemented correctly ?
        return null;
    }



}
