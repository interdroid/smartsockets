package ibis.smartsockets.direct;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import java.nio.channels.SocketChannel;

import ch.ethz.ssh2.LocalStreamForwarder;

public class DirectSSHSocket extends DirectSocket {

    private final LocalStreamForwarder lsf;
    private boolean closed = false;
    
    public DirectSSHSocket(DirectSocketAddress local, DirectSocketAddress remote, 
            InputStream in, OutputStream out, LocalStreamForwarder lsf) {
        
        super(local, remote, in, out);
    
        this.lsf = lsf;
    }

    public void close() throws IOException {
        lsf.close();
        closed = true;
    }

    @Override
    public SocketChannel getChannel() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int getReceiveBufferSize() throws SocketException {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public boolean getReuseAddress() throws SocketException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public int getSendBufferSize() throws SocketException {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int getSoLinger() throws SocketException {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int getSoTimeout() throws SocketException {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public boolean getTcpNoDelay() throws SocketException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public boolean isInputShutdown() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isOutputShutdown() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void setReceiveBufferSize(int sz) throws SocketException {
        // TODO Auto-generated method stub        
    }

    @Override
    public void setReuseAddress(boolean on) throws SocketException {
        // TODO Auto-generated method stub        
    }

    @Override
    public void setSendBufferSize(int sz) throws SocketException {
        // TODO Auto-generated method stub        
    }

    @Override
    public void setSoLinger(boolean on, int linger) throws SocketException {
        // TODO Auto-generated method stub       
    }

    @Override
    public void setSoTimeout(int t) throws SocketException {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void setTcpNoDelay(boolean on) throws SocketException {
        // TODO Auto-generated method stub        
    }

    @Override
    public void shutdownInput() throws IOException {
        // TODO Auto-generated method stub        
    }

    @Override
    public void shutdownOutput() throws IOException {
        // TODO Auto-generated method stub        
    }

    @Override
    public int getLocalPort() throws IOException {
        // TODO Auto-generated method stub
        return 0;
    }
}
