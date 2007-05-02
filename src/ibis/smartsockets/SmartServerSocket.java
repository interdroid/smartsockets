package ibis.smartsockets;

import ibis.smartsockets.virtual.VirtualServerSocket;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.ServerSocketChannel;


public class SmartServerSocket extends ServerSocket {

    private VirtualServerSocket ss;
    
    protected SmartServerSocket(VirtualServerSocket ss) throws IOException {
        this.ss = ss;
    }

    /**
     * @return
     * @throws IOException
     * @see java.net.ServerSocket#accept()
     */
    public Socket accept() throws IOException {
        return new SmartSocket(ss.accept());
    }

    /**
     * @param endpoint
     * @param backlog
     * @throws IOException
     * @see java.net.ServerSocket#bind(java.net.SocketAddress, int)
     */
    public void bind(SocketAddress endpoint, int backlog) throws IOException {
        ss.bind(endpoint, backlog);
    }

    /**
     * @param endpoint
     * @throws IOException
     * @see java.net.ServerSocket#bind(java.net.SocketAddress)
     */
    public void bind(SocketAddress endpoint) throws IOException {
        bind(endpoint, 50);
    }

    /**
     * @throws IOException
     * @see java.net.ServerSocket#close()
     */
    public void close() throws IOException {
        ss.close();
    }

    /**
     * @param obj
     * @return
     * @see java.lang.Object#equals(java.lang.Object)
     */
    public boolean equals(Object obj) {
        return ss.equals(obj);
    }

    /**
     * @return
     * @see java.net.ServerSocket#getChannel()
     */
    public ServerSocketChannel getChannel() {
        return ss.getChannel();
    }

    /**
     * @return
     * @see java.net.ServerSocket#getInetAddress()
     */
    public InetAddress getInetAddress() {
        return ss.getInetAddress();
    }

    /**
     * @return
     * @see java.net.ServerSocket#getLocalPort()
     */
    public int getLocalPort() {
        return ss.getLocalPort();
    }

    /**
     * @return
     * @see java.net.ServerSocket#getLocalSocketAddress()
     */
    public SocketAddress getLocalSocketAddress() {
        return ss.getLocalSocketAddress();
    }

    /**
     * @return
     * @throws SocketException
     * @see java.net.ServerSocket#getReceiveBufferSize()
     */
    public int getReceiveBufferSize() throws SocketException {
        return ss.getReceiveBufferSize();
    }

    /**
     * @return
     * @throws SocketException
     * @see java.net.ServerSocket#getReuseAddress()
     */
    public boolean getReuseAddress() throws SocketException {
        return ss.getReuseAddress();
    }

    /**
     * @return
     * @throws IOException
     * @see java.net.ServerSocket#getSoTimeout()
     */
    public int getSoTimeout() throws IOException {
        return ss.getSoTimeout();
    }

    /**
     * @return
     * @see java.lang.Object#hashCode()
     */
    public int hashCode() {
        return ss.hashCode();
    }

    /**
     * @return
     * @see java.net.ServerSocket#isBound()
     */
    public boolean isBound() {
        return ss.isBound();
    }

    /**
     * @return
     * @see java.net.ServerSocket#isClosed()
     */
    public boolean isClosed() {
        return ss.isClosed();
    }

    /**
     * @param connectionTime
     * @param latency
     * @param bandwidth
     * @see java.net.ServerSocket#setPerformancePreferences(int, int, int)
     */
    public void setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
        ss.setPerformancePreferences(connectionTime, latency, bandwidth);
    }

    /**
     * @param size
     * @throws SocketException
     * @see java.net.ServerSocket#setReceiveBufferSize(int)
     */
    public void setReceiveBufferSize(int size) throws SocketException {
        ss.setReceiveBufferSize(size);
    }

    /**
     * @param on
     * @throws SocketException
     * @see java.net.ServerSocket#setReuseAddress(boolean)
     */
    public void setReuseAddress(boolean on) throws SocketException {
        ss.setReuseAddress(on);
    }

    /**
     * @param timeout
     * @throws SocketException
     * @see java.net.ServerSocket#setSoTimeout(int)
     */
    public void setSoTimeout(int timeout) throws SocketException {
        ss.setSoTimeout(timeout);
    }

    /**
     * @return
     * @see java.net.ServerSocket#toString()
     */
    public String toString() {
        return ss.toString();
    }

}
