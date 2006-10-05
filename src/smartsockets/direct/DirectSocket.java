package smartsockets.direct;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.SocketChannel;

public class DirectSocket {

    private Socket socket;
    
    DirectSocket(Socket socket) { 
        /*super(null);*/
        this.socket = socket;        
    }
    
    public void close() throws IOException {
        socket.close();
    }

    public SocketChannel getChannel() {
        return socket.getChannel();
    }
    
    public InputStream getInputStream() throws IOException {
        return socket.getInputStream();
    }

    public boolean getKeepAlive() throws SocketException {
        return socket.getKeepAlive();
    }
    
    /*
    public InetAddress getInetAddress() {
        // TODO: NOT SURE
        return socket.getInetAddress();
    }
    */
    public InetAddress getLocalAddress() {
        // TODO: NOT SURE
        return socket.getLocalAddress();     
    }

    public int getLocalPort() {
        // TODO: NOT SURE
        return socket.getLocalPort();
    }

    public SocketAddress getLocalSocketAddress() {
        // TODO: NOT SURE
        return socket.getLocalSocketAddress();
    }

    public int getPort() {
        // TODO: NOT SURE
        return socket.getPort();        
    }
    
    public SocketAddress getRemoteSocketAddress() {
        // TODO: NOT SURE
        return socket.getRemoteSocketAddress();
    }
    
    public boolean getOOBInline() throws SocketException {
        return socket.getOOBInline();       
    }

    public OutputStream getOutputStream() throws IOException {
        return socket.getOutputStream();
    }
    
    public int getReceiveBufferSize() throws SocketException {
        return socket.getReceiveBufferSize();
    }

    public boolean getReuseAddress() throws SocketException {
        return socket.getReuseAddress();
    }

    public int getSendBufferSize() throws SocketException {
        return socket.getSendBufferSize();
    }

    public int getSoLinger() throws SocketException {
        return socket.getSoLinger();
    }

    public int getSoTimeout() throws SocketException {
        return socket.getSoTimeout();        
    }

    public boolean getTcpNoDelay() throws SocketException {
        return socket.getTcpNoDelay();
    }

    public int getTrafficClass() throws SocketException {
        return socket.getTrafficClass();
    }

    public boolean isBound() {
        return socket.isBound();
    }

    public boolean isClosed() {
        return socket.isClosed();
    }

    public boolean isConnected() {
        return socket.isConnected();
    }

    public boolean isInputShutdown() {
        return socket.isInputShutdown();
    }

    public boolean isOutputShutdown() {
        return socket.isOutputShutdown();
    }

    public void sendUrgentData(int data) throws IOException {
        socket.sendUrgentData(data);
    }

    public void setKeepAlive(boolean on) throws SocketException {
        socket.setKeepAlive(on);
    }

    public void setOOBInline(boolean on) throws SocketException {
        socket.setOOBInline(on);
    }

    public void setReceiveBufferSize(int sz) throws SocketException {
        socket.setReceiveBufferSize(sz);
    }

    public void setReuseAddress(boolean on) throws SocketException {
        socket.setReuseAddress(on);
    }

    public void setSendBufferSize(int sz) throws SocketException {
        socket.setSendBufferSize(sz);
    }

    public void setSoLinger(boolean on, int linger) throws SocketException {
        socket.setSoLinger(on, linger);
    }

    public void setSoTimeout(int t) throws SocketException {
        socket.setSoTimeout(t);
    }

    public void setTcpNoDelay(boolean on) throws SocketException {
        socket.setTcpNoDelay(on);
    }

    public void setTrafficClass(int tc) throws SocketException {
        socket.setTrafficClass(tc);
    }

    public void shutdownInput() throws IOException {
        socket.shutdownInput();
    }

    public void shutdownOutput() throws IOException {
        socket.shutdownOutput();
    }

    public String toString() {
        return "PlainSocket(" + socket.toString() + ")";
    }    
}
