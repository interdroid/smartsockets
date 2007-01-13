package smartsockets.direct;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import java.nio.channels.SocketChannel;

public abstract class DirectSocket {

    protected final InputStream in;
    protected final OutputStream out;
    
    protected final SocketAddressSet local;
    protected final SocketAddressSet remote;
    
    private int userData;
        
    DirectSocket(SocketAddressSet local, SocketAddressSet remote, 
            InputStream in, OutputStream out) {
        
        this.local = local;
        this.remote = remote;
        
        this.in = in; 
        this.out = out;
    }
    
    public int getUserData() { 
        return userData;
    }
    
    public void setUserData(int userData) { 
        this.userData = userData;
    }
    
    public InputStream getInputStream() throws IOException {
        return in;
    }

    public OutputStream getOutputStream() throws IOException {
        return out;
    }
        
    public SocketAddressSet getLocalAddress() {
        return local;     
    }
      
    public SocketAddressSet getRemoteAddress() {
        return remote;
    }
    
    public boolean isBound() {
        return true;
    }

    public boolean isConnected() {
        return !isClosed();
    }
    
    public abstract int getLocalPort() throws IOException;
    
    public abstract void close() throws IOException;
    public abstract boolean isClosed();

    public abstract void setReceiveBufferSize(int sz) throws SocketException;
    public abstract int getReceiveBufferSize() throws SocketException;
    
    public abstract void setSendBufferSize(int sz) throws SocketException;
    public abstract int getSendBufferSize() throws SocketException;    
    
    public abstract void setSoTimeout(int t) throws SocketException;
    public abstract int getSoTimeout() throws SocketException;
    
    public abstract void setTcpNoDelay(boolean on) throws SocketException;
    public abstract boolean getTcpNoDelay() throws SocketException;

    public abstract SocketChannel getChannel();
    
    public abstract void setSoLinger(boolean on, int linger) throws SocketException;
    public abstract int getSoLinger() throws SocketException;
    
    public abstract void setReuseAddress(boolean on) throws SocketException;
    public abstract boolean getReuseAddress() throws SocketException;

    public abstract void shutdownInput() throws IOException;
    public abstract boolean isInputShutdown();
    
    public abstract void shutdownOutput() throws IOException;
    public abstract boolean isOutputShutdown();
   
}
