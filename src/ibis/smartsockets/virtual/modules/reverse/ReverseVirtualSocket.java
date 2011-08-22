package ibis.smartsockets.virtual.modules.reverse;

import ibis.smartsockets.virtual.VirtualSocket;
import ibis.smartsockets.virtual.modules.AbstractDirectModule;
import ibis.smartsockets.virtual.modules.direct.DirectVirtualSocket;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.SocketChannel;
import java.util.Map;

public class ReverseVirtualSocket extends VirtualSocket {

    private final DirectVirtualSocket s;

    protected ReverseVirtualSocket(DirectVirtualSocket s) {
        this.s = s;
    }

    protected void connectionAccepted(int timeout) throws IOException {

        int ack1 = -1;
        int ack2 = -1;

        try {
            s.setSoTimeout(timeout);
            s.setTcpNoDelay(true);

            OutputStream out = s.getOutputStream();
            InputStream in = s.getInputStream();

            // This is a bit nasty... We need to send the accept twice, and
            // read the reply twice, simply because the connection setup is
            // 'doubled' by first doing a reverse setup followed by a 'normal'
            // setup
            out.write(AbstractDirectModule.ACCEPT);
            out.write(AbstractDirectModule.ACCEPT);
            out.flush();

            // We should do a three way handshake here to ensure both side agree
            // that we have a connection...
            ack1 = in.read();
            ack2 = in.read();

            if (ack1 == -1 || ack2 == -1) {
                throw new EOFException("Reverse connection handshake failed: "
                        + " Unexpected EOF");
            } else if (ack1 != AbstractDirectModule.ACCEPT ||
                    ack2 != AbstractDirectModule.ACCEPT) {
                throw new ConnectException("Client disconnected");
            }

            s.setSoTimeout(0);
            s.setTcpNoDelay(false);
        } catch (IOException e) {
            s.close();
            throw e;
        }
    }

    /**
     * @throws IOException
     * @see ibis.smartsockets.virtual.modules.direct.DirectVirtualSocket#close()
     */
    public void close() throws IOException {
        s.close();
    }

    /**
     * @param timeout
     * @param opcode
     * @see ibis.smartsockets.virtual.modules.direct.DirectVirtualSocket#connectionRejected(int, byte)
     */
    public void connectionRejected(int timeout, byte opcode) {
        s.connectionRejected(timeout, opcode);
    }

    /**
     * @param timeout
     * @see ibis.smartsockets.virtual.modules.direct.DirectVirtualSocket#connectionRejected(int)
     */
    public void connectionRejected(int timeout) {
        s.connectionRejected(timeout);
    }

    /**
     * @param obj
     * @see java.lang.Object#equals(java.lang.Object)
     */
    public boolean equals(Object obj) {
        return s.equals(obj);
    }

    /**
     * @see ibis.smartsockets.virtual.modules.direct.DirectVirtualSocket#getChannel()
     */
    public SocketChannel getChannel() {
        return s.getChannel();
    }

    /**
     * @see ibis.smartsockets.virtual.VirtualSocket#getInetAddress()
     */
    public InetAddress getInetAddress() {
        return s.getInetAddress();
    }

    /**
     * @throws IOException
     * @see ibis.smartsockets.virtual.modules.direct.DirectVirtualSocket#getInputStream()
     */
    public InputStream getInputStream() throws IOException {
        return s.getInputStream();
    }

    /**
     * @throws SocketException
     * @see ibis.smartsockets.virtual.VirtualSocket#getKeepAlive()
     */
    public boolean getKeepAlive() throws SocketException {
        return s.getKeepAlive();
    }

    /**
     * @see ibis.smartsockets.virtual.VirtualSocket#getLocalAddress()
     */
    public InetAddress getLocalAddress() {
        return s.getLocalAddress();
    }

    /**
     * @see ibis.smartsockets.virtual.VirtualSocket#getLocalPort()
     */
    public int getLocalPort() {
        return s.getLocalPort();
    }

    /**
     * @see ibis.smartsockets.virtual.VirtualSocket#getLocalSocketAddress()
     */
    public SocketAddress getLocalSocketAddress() {
        return s.getLocalSocketAddress();
    }

    /**
     * @throws SocketException
     * @see ibis.smartsockets.virtual.VirtualSocket#getOOBInline()
     */
    public boolean getOOBInline() throws SocketException {
        return s.getOOBInline();
    }

    /**
     * @throws IOException
     * @see ibis.smartsockets.virtual.modules.direct.DirectVirtualSocket#getOutputStream()
     */
    public OutputStream getOutputStream() throws IOException {
        return s.getOutputStream();
    }

    /**
     * @see ibis.smartsockets.virtual.VirtualSocket#getPort()
     */
    public int getPort() {
        return s.getPort();
    }

    /**
     * @param key
     * @see ibis.smartsockets.virtual.VirtualSocket#getProperty(java.lang.String)
     */
    public Object getProperty(String key) {
        return s.getProperty(key);
    }

    /**
     * @throws SocketException
     * @see ibis.smartsockets.virtual.modules.direct.DirectVirtualSocket#getReceiveBufferSize()
     */
    public int getReceiveBufferSize() throws SocketException {
        return s.getReceiveBufferSize();
    }

    /**
     * @see ibis.smartsockets.virtual.VirtualSocket#getRemoteSocketAddress()
     */
    public SocketAddress getRemoteSocketAddress() {
        return s.getRemoteSocketAddress();
    }

    /**
     * @throws SocketException
     * @see ibis.smartsockets.virtual.modules.direct.DirectVirtualSocket#getReuseAddress()
     */
    public boolean getReuseAddress() throws SocketException {
        return s.getReuseAddress();
    }

    /**
     * @throws SocketException
     * @see ibis.smartsockets.virtual.modules.direct.DirectVirtualSocket#getSendBufferSize()
     */
    public int getSendBufferSize() throws SocketException {
        return s.getSendBufferSize();
    }

    /**
     * @throws SocketException
     * @see ibis.smartsockets.virtual.modules.direct.DirectVirtualSocket#getSoLinger()
     */
    public int getSoLinger() throws SocketException {
        return s.getSoLinger();
    }

    /**
     * @throws SocketException
     * @see ibis.smartsockets.virtual.modules.direct.DirectVirtualSocket#getSoTimeout()
     */
    public int getSoTimeout() throws SocketException {
        return s.getSoTimeout();
    }

    /**
     * @throws SocketException
     * @see ibis.smartsockets.virtual.modules.direct.DirectVirtualSocket#getTcpNoDelay()
     */
    public boolean getTcpNoDelay() throws SocketException {
        return s.getTcpNoDelay();
    }

    /**
     * @throws SocketException
     * @see ibis.smartsockets.virtual.VirtualSocket#getTrafficClass()
     */
    public int getTrafficClass() throws SocketException {
        return s.getTrafficClass();
    }

    /**
     * @see java.lang.Object#hashCode()
     */
    public int hashCode() {
        return s.hashCode();
    }

    /**
     * @see ibis.smartsockets.virtual.modules.direct.DirectVirtualSocket#isBound()
     */
    public boolean isBound() {
        return s.isBound();
    }

    /**
     * @see ibis.smartsockets.virtual.modules.direct.DirectVirtualSocket#isClosed()
     */
    public boolean isClosed() {
        return s.isClosed();
    }

    /**
     * @see ibis.smartsockets.virtual.modules.direct.DirectVirtualSocket#isConnected()
     */
    public boolean isConnected() {
        return s.isConnected();
    }

    /**
     * @see ibis.smartsockets.virtual.modules.direct.DirectVirtualSocket#isInputShutdown()
     */
    public boolean isInputShutdown() {
        return s.isInputShutdown();
    }

    /**
     * @see ibis.smartsockets.virtual.modules.direct.DirectVirtualSocket#isOutputShutdown()
     */
    public boolean isOutputShutdown() {
        return s.isOutputShutdown();
    }

    /**
     * @see ibis.smartsockets.virtual.VirtualSocket#properties()
     */
    public Map<String, Object> properties() {
        return s.properties();
    }

    /**
     * @param data
     * @throws IOException
     * @see ibis.smartsockets.virtual.VirtualSocket#sendUrgentData(int)
     */
    public void sendUrgentData(int data) throws IOException {
        s.sendUrgentData(data);
    }

    /**
     * @param on
     * @throws SocketException
     * @see ibis.smartsockets.virtual.VirtualSocket#setKeepAlive(boolean)
     */
    public void setKeepAlive(boolean on) throws SocketException {
        s.setKeepAlive(on);
    }

    /**
     * @param on
     * @throws SocketException
     * @see ibis.smartsockets.virtual.VirtualSocket#setOOBInline(boolean)
     */
    public void setOOBInline(boolean on) throws SocketException {
        s.setOOBInline(on);
    }

    /**
     * @param connectionTime
     * @param latency
     * @param bandwidth
     * @see ibis.smartsockets.virtual.VirtualSocket#setPerformancePreferences(int, int, int)
     */
    public void setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
        s.setPerformancePreferences(connectionTime, latency, bandwidth);
    }

    /**
     * @param properties
     * @see ibis.smartsockets.virtual.VirtualSocket#setProperties(java.util.Map)
     */
    public void setProperties(Map<String, Object> properties) {
        s.setProperties(properties);
    }

    /**
     * @param key
     * @param val
     * @see ibis.smartsockets.virtual.VirtualSocket#setProperty(java.lang.String, java.lang.Object)
     */
    public void setProperty(String key, Object val) {
        s.setProperty(key, val);
    }

    /**
     * @param sz
     * @throws SocketException
     * @see ibis.smartsockets.virtual.modules.direct.DirectVirtualSocket#setReceiveBufferSize(int)
     */
    public void setReceiveBufferSize(int sz) throws SocketException {
        s.setReceiveBufferSize(sz);
    }

    /**
     * @param on
     * @throws SocketException
     * @see ibis.smartsockets.virtual.modules.direct.DirectVirtualSocket#setReuseAddress(boolean)
     */
    public void setReuseAddress(boolean on) throws SocketException {
        s.setReuseAddress(on);
    }

    /**
     * @param sz
     * @throws SocketException
     * @see ibis.smartsockets.virtual.modules.direct.DirectVirtualSocket#setSendBufferSize(int)
     */
    public void setSendBufferSize(int sz) throws SocketException {
        s.setSendBufferSize(sz);
    }

    /**
     * @param on
     * @param linger
     * @throws SocketException
     * @see ibis.smartsockets.virtual.modules.direct.DirectVirtualSocket#setSoLinger(boolean, int)
     */
    public void setSoLinger(boolean on, int linger) throws SocketException {
        s.setSoLinger(on, linger);
    }

    /**
     * @param t
     * @throws SocketException
     * @see ibis.smartsockets.virtual.modules.direct.DirectVirtualSocket#setSoTimeout(int)
     */
    public void setSoTimeout(int t) throws SocketException {
        s.setSoTimeout(t);
    }

    /**
     * @param on
     * @throws SocketException
     * @see ibis.smartsockets.virtual.modules.direct.DirectVirtualSocket#setTcpNoDelay(boolean)
     */
    public void setTcpNoDelay(boolean on) throws SocketException {
        s.setTcpNoDelay(on);
    }

    /**
     * @param tc
     * @throws SocketException
     * @see ibis.smartsockets.virtual.VirtualSocket#setTrafficClass(int)
     */
    public void setTrafficClass(int tc) throws SocketException {
        s.setTrafficClass(tc);
    }

    /**
     * @throws IOException
     * @see ibis.smartsockets.virtual.modules.direct.DirectVirtualSocket#shutdownInput()
     */
    public void shutdownInput() throws IOException {
        s.shutdownInput();
    }

    /**
     * @throws IOException
     * @see ibis.smartsockets.virtual.modules.direct.DirectVirtualSocket#shutdownOutput()
     */
    public void shutdownOutput() throws IOException {
        s.shutdownOutput();
    }

    /**
     * @see ibis.smartsockets.virtual.modules.direct.DirectVirtualSocket#toString()
     */
    public String toString() {
        return s.toString();
    }

    /**
     * @param timeout
     * @throws IOException
     * @see ibis.smartsockets.virtual.modules.direct.DirectVirtualSocket#waitForAccept(int)
     */
    public void waitForAccept(int timeout) throws IOException {
        s.waitForAccept(timeout);
    }


}
