package ibis.smartsockets;

import ibis.smartsockets.virtual.VirtualSocket;
import ibis.smartsockets.virtual.VirtualSocketAddress;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;


public class SmartSocket extends Socket {

    private VirtualSocket s;
    private boolean bound;
    private SocketAddress bindpoint;
    
    private Map<String, Object> properties = new HashMap<String, Object>();
    
    private boolean tcpNoDelay = false;
    private boolean solinger = false;
    private boolean soreuse = false;
    private boolean keepalive = false;
    private boolean OOBinline = false;
    
    private int linger = 0; 
    private int timeout = 0;
    private int receiveBufferSize = -1;
    private int sendBufferSize = -1;
    private int trafficClass = -1;
    
    SmartSocket() {
        bound = false;
    }
    
    SmartSocket(VirtualSocket s) { 
        this.s = s;
        bound = true;
    }
    
    /**
     * @param bindpoint
     * @throws IOException
     * @see java.net.Socket#bind(java.net.SocketAddress)
     */
    public void bind(SocketAddress bindpoint) throws IOException {
        
        if (isClosed()) {
            throw new SocketException("Socket is closed");
        }
        
        if (isBound()) {
            throw new SocketException("Already bound");
        }
        
        if (bindpoint == null) { 
            throw new IllegalArgumentException("Bindpoint is null");
        }
         
        if (!(bindpoint instanceof InetSocketAddress || 
                bindpoint instanceof VirtualSocketAddress)) { 
            throw new IllegalArgumentException("Unsupported address type!");
        }
        
        // TODO: should check here is the bindpoint is legal ? Problem is that 
        // it does not necessarily have to result in a real socket....
        this.bindpoint = bindpoint;
        bound = true;
    }

    /**
     * @throws IOException
     * @see java.net.Socket#close() 
     */
    public void close() throws IOException {
        
        if (isClosed()) {
            return;
        }
            
        if (isConnected()) {
            s.close();
        }
    }

    /**
     * @param endpoint
     * @param timeout
     * @throws IOException
     * @see java.net.Socket#connect(java.net.SocketAddress, int)
     */
    public void connect(SocketAddress endpoint, int timeout) throws IOException {
        
        if (isClosed()) {
            throw new SocketException("Socket is closed");
        }
        
        if (endpoint == null) {
            throw new IllegalArgumentException("The target address is null!");
        }
            
        if (timeout < 0) {
          throw new IllegalArgumentException("Negative timeout");
        }
        
        if (isConnected()) { 
            throw new SocketException("Already connected!");
        }
        
        VirtualSocketAddress tmp = null;
        
        if (!(endpoint instanceof VirtualSocketAddress)) {
            throw new IllegalArgumentException("Unsupported address type");
        }
        
        tmp = (VirtualSocketAddress) endpoint;
        
        s = SmartSocketFactory.getDefault().connect(tmp, timeout, properties);
        bound = true;
        
        // No longer used...
        properties = null;
    }

    /**
     * @param endpoint
     * @throws IOException
     * @see java.net.Socket#connect(java.net.SocketAddress)
     */
    public void connect(SocketAddress endpoint) throws IOException {
        connect(endpoint, 0);
    }

    /**
     * @return
     * @throws SocketException 
     * @see java.net.Socket#getChannel()
     */
    public SocketChannel getChannel() {

        if (isClosed() || !isConnected()) {
            return null;
        }
        
        return s.getChannel();
    }

    /**
     * @return
     * @throws SocketException 
     * @see java.net.Socket#getInetAddress()
     */
    public InetAddress getInetAddress() {
        
        if (isClosed() || !isConnected()) {
            return null;
        }
        
        return s.getInetAddress();
    }

    /**
     * @return
     * @throws IOException
     * @see java.net.Socket#getInputStream()
     */
    public InputStream getInputStream() throws IOException {
        
        if (isClosed()) {
            throw new SocketException("Socket is closed");
        }
            
        if (!isConnected()) {
            throw new SocketException("Socket is not connected");
        }
        
        return s.getInputStream();
    }

    /**
     * @return
     * @throws SocketException
     * @see java.net.Socket#getKeepAlive()
     */
    public boolean getKeepAlive() throws SocketException {
        
        if (s == null) { 
            return keepalive;
        }
        
        return s.getKeepAlive();
    }

    /**
     * @return
     * @see java.net.Socket#getLocalAddress()
     */
    public InetAddress getLocalAddress() {
        
        if (!isBound()) { 
            return null;
        }
        
        if (s != null) { 
            return s.getLocalAddress();
        } 
        
        if (bindpoint instanceof InetSocketAddress) { 
            return ((InetSocketAddress) bindpoint).getAddress();
        }
        
        // NOTE: May happen if we are bound to a VirtualSocketAddress. 
        return null;
    }

    /**
     * @return
     * @see java.net.Socket#getLocalPort()
     */
    public int getLocalPort() {
        
        if (!isBound()) { 
            return -1;
        }
        
        if (s != null) { 
            return s.getLocalPort();
        } 
        
        if (bindpoint instanceof InetSocketAddress) { 
            return ((InetSocketAddress) bindpoint).getPort();
        }
        
        if (bindpoint instanceof VirtualSocketAddress) { 
            return ((VirtualSocketAddress) bindpoint).port();
        }
        
        // NOTE: Should not happen! 
        return -1;
    }

    /**
     * @return
     * @see java.net.Socket#getLocalSocketAddress()
     */
    public SocketAddress getLocalSocketAddress() {
        
        if (!isBound()) { 
            return null;
        }
        
        if (s != null) {
            return s.getLocalSocketAddress();
        }
        
        return bindpoint;
    }

    /**
     * @return
     * @throws SocketException
     * @see java.net.Socket#getOOBInline()
     */
    public boolean getOOBInline() throws SocketException {
        if (s == null) { 
            return OOBinline;
        }
        
        return s.getOOBInline();
    }

    /**
     * @return
     * @throws IOException
     * @see java.net.Socket#getOutputStream()
     */
    public OutputStream getOutputStream() throws IOException {
        
        if (isClosed()) {
            throw new SocketException("Socket is closed");
        }
            
        if (!isConnected()) {
            throw new SocketException("Socket is not connected");
        }
        
        return s.getOutputStream();
    }

    /**
     * @return
     * @see java.net.Socket#getPort()
     */
    public int getPort() {
        
        if (s == null) {
            return 0;
        }
        
        return s.getPort();
    }

    /**
     * @return
     * @throws SocketException
     * @see java.net.Socket#getReceiveBufferSize()
     */
    public int getReceiveBufferSize() throws SocketException {
        
        if (s == null) { 
            return receiveBufferSize;
        }
        
        return s.getReceiveBufferSize();
    }

    /**
     * @return
     * @throws SocketException 
     * @see java.net.Socket#getRemoteSocketAddress()
     */
    public SocketAddress getRemoteSocketAddress() {
        
        if (s == null) { 
            return null;
        }
        
        return s.getRemoteSocketAddress();
    }

    /**
     * @return
     * @throws SocketException
     * @see java.net.Socket#getReuseAddress()
     */
    public boolean getReuseAddress() throws SocketException {
        
        if (s == null) { 
            return soreuse;
        }
        
        return s.getReuseAddress();
    }

    /**
     * @return
     * @throws SocketException
     * @see java.net.Socket#getSendBufferSize()
     */
    public int getSendBufferSize() throws SocketException {
        
        if (s == null) { 
            return sendBufferSize;
        }
        
        return s.getSendBufferSize();
    }

    /**
     * @return
     * @throws SocketException
     * @see java.net.Socket#getSoLinger()
     */
    public int getSoLinger() throws SocketException {
        
        if (s == null) {
            
            if (solinger) {     
                return linger;
            } else { 
                return -1;
            }
        }
        
        return s.getSoLinger();
    }

    /**
     * @return
     * @throws SocketException
     * @see java.net.Socket#getSoTimeout()
     */
    public int getSoTimeout() throws SocketException {
        
        if (s == null) { 
            return timeout;
        }
        
        return s.getSoTimeout();
    }

    /**
     * @return
     * @throws SocketException
     * @see java.net.Socket#getTcpNoDelay()
     */
    public boolean getTcpNoDelay() throws SocketException {
        
        if (s == null) { 
            return tcpNoDelay;
        }
        
        return s.getTcpNoDelay();
    }

    /**
     * @return
     * @throws SocketException
     * @see java.net.Socket#getTrafficClass()
     */
    public int getTrafficClass() throws SocketException {
        
        if (s == null) { 
            return trafficClass;
        }
        
        return s.getTrafficClass();
    }

    /**
     * @return
     * @see java.net.Socket#isBound()
     */
    public boolean isBound() {
        return bound;
    }

    /**
     * @return
     * @see java.net.Socket#isClosed()
     */
    public boolean isClosed() {
        
        if (s != null) {    
            return s.isClosed();
        }
        
        // NOTE: When the socket has not been created yet, it isn't closed. 
        return false;
    }

    /**
     * @return
     * @see java.net.Socket#isConnected()
     */
    public boolean isConnected() {
        
        if (s != null) { 
            return s.isConnected();
        }
        
        return false;
    }

    /**
     * @return
     * @see java.net.Socket#isInputShutdown()
     */
    public boolean isInputShutdown() {
        
        if (s != null) { 
            return s.isInputShutdown();
        } 
        
        return false;
    }

    /**
     * @return
     * @see java.net.Socket#isOutputShutdown()
     */
    public boolean isOutputShutdown() {
        
        if (s != null) { 
            return s.isOutputShutdown();
        }
        
        return false;
    } 
    /**
     * @param data
     * @throws IOException
     * @see java.net.Socket#sendUrgentData(int)
     */
    public void sendUrgentData(int data) throws IOException {
        
        if (isClosed()) {
            throw new SocketException("Socket is closed");
        }
        
        if (!isConnected()) {
            throw new SocketException("Socket is not connected");
        }
        
        s.sendUrgentData(data);
    }

    /**
     * @param on
     * @throws SocketException
     * @see java.net.Socket#setKeepAlive(boolean)
     */
    public void setKeepAlive(boolean on) throws SocketException {
     
        if (s != null) { 
            s.setKeepAlive(on);
        } else { 
            properties.put("tcp.keepAlive", on);
        }
        
        keepalive = on;
    }

    /**
     * @param on
     * @throws SocketException
     * @see java.net.Socket#setOOBInline(boolean)
     */
    public void setOOBInline(boolean on) throws SocketException {
        if (s != null) { 
            s.setOOBInline(on);
        } else { 
            properties.put("tcp.OOBinline", on);
        }
        
        OOBinline = on;
    }
    
    /**
     * @param connectionTime
     * @param latency
     * @param bandwidth
     * @see java.net.Socket#setPerformancePreferences(int, int, int)
     */
    public void setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
        
        // TODO: implement!
        // s.setPerformancePreferences(connectionTime, latency, bandwidth);
    }

    /**
     * @param size
     * @throws SocketException
     * @see java.net.Socket#setReceiveBufferSize(int)
     */
    public void setReceiveBufferSize(int size) throws SocketException {
        
        if (s != null) { 
            s.setReceiveBufferSize(size);
        } else { 
            properties.put("tcp.receiveBufferSize", size);
        }
        
        receiveBufferSize = size;
    }

    /**
     * @param on
     * @throws SocketException
     * @see java.net.Socket#setReuseAddress(boolean)
     */
    public void setReuseAddress(boolean on) throws SocketException {
        
        if (s != null) { 
            s.setReuseAddress(on);
        } else { 
            properties.put("tcp.reuseAddress", on);
        }
        
        soreuse = on;
    }

    /**
     * @param size
     * @throws SocketException
     * @see java.net.Socket#setSendBufferSize(int)
     */
    public void setSendBufferSize(int size) throws SocketException {
        
        if (s != null) { 
            s.setSendBufferSize(size);
        } else { 
            properties.put("tcp.sendBufferSize", size);
        }
        
        sendBufferSize = size;
    }

    /**
     * @param on
     * @param linger
     * @throws SocketException
     * @see java.net.Socket#setSoLinger(boolean, int)
     */
    public void setSoLinger(boolean on, int linger) throws SocketException {
        if (s != null) { 
            s.setSoLinger(on, linger);
        } else { 
            properties.put("tcp.linger", linger);
        }
        
        this.solinger = on;
        this.linger = linger;
    }

    /**
     * @param timeout
     * @throws SocketException
     * @see java.net.Socket#setSoTimeout(int)
     */
    public void setSoTimeout(int timeout) throws SocketException {
        
        if (s != null) { 
            s.setSoTimeout(timeout);
        } else { 
            properties.put("tcp.timeout", timeout);
        }
        
        this.timeout = timeout;
    }

    /**
     * @param on
     * @throws SocketException
     * @see java.net.Socket#setTcpNoDelay(boolean)
     */
    public void setTcpNoDelay(boolean on) throws SocketException {
        if (s != null) { 
            s.setTcpNoDelay(on);
        } else { 
            properties.put("tcp.nodelay", on);
        }
        
        tcpNoDelay = on;
    }

    /**
     * @param tc
     * @throws SocketException
     * @see java.net.Socket#setTrafficClass(int)
     */
    public void setTrafficClass(int tc) throws SocketException {
        
        if (s != null) { 
            s.setTrafficClass(tc);
        } else { 
            properties.put("tcp.trafficClass", tc);
        }
        
        trafficClass = tc;
    }

    /**
     * @throws IOException
     * @see java.net.Socket#shutdownInput()
     */
    public void shutdownInput() throws IOException {
        
        if (isClosed()) {
            throw new SocketException("Socket is closed");
        }
        
        if (!isConnected()) {
            throw new SocketException("Socket is not connected");
        }
        
        s.shutdownInput();
    }

    /**
     * @throws IOException
     * @see java.net.Socket#shutdownOutput()
     */
    public void shutdownOutput() throws IOException {
        
        if (isClosed()) {
            throw new SocketException("Socket is closed");
        }
        
        if (!isConnected()) {
            throw new SocketException("Socket is not connected");
        }
    
        s.shutdownOutput();
    }

    /**
     * @return
     * @see java.net.Socket#toString()
     */
    public String toString() {
        return "SmartSocket(" + (s == null ? "<not connected>" : s.toString()) 
            + ")";
    }
}
