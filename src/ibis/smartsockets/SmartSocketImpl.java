package ibis.smartsockets;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketImpl;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.Arrays;

// INITIAL IMPLEMENTATION -- DO NOT USE!!

public class SmartSocketImpl extends SocketImpl {

    private Socket socket;
    
    private SmartSocketImplFactory factory;
    
    protected SmartSocketImpl(SmartSocketImplFactory factory) { 
        this.factory = factory;
        
        try {
            AccessController.doPrivileged(new PrivilegedExceptionAction() {
                public Object run() throws Exception {
               
                    try { 
                        Class c1 = Class.forName("java.net.PlainSocketImpl");
                        Constructor [] x = c1.getConstructors();
                        
                        System.out.println("Conss: " + x.length + " " + Arrays.deepToString(x));
                       
                        Constructor co1 = c1.getConstructor(new Class [0]);
                        co1.setAccessible(true);
                        SocketImpl si = (SocketImpl) co1.newInstance();
                    
                        Class c2 = Class.forName("java.net.Socket");
                        Constructor c = c2.getConstructor(new Class [] { SocketImpl.class });
                        c.setAccessible(true);
                        socket = (Socket) c.newInstance(new Object [] { si });
                    
                        System.out.println("Created socket!!!");
                    } catch (Exception e) { 
                        System.out.println("Oops!");
                        e.printStackTrace();
                    
                    }
                        
                return null;
                }
            });
        } catch (Exception e) {
            System.out.println("Oops!");
            e.printStackTrace();
        }
        
    }
    
    @Override
    protected void accept(SocketImpl s) throws IOException {
        
        System.out.println("In SmartSocketImpl.accept()");
        
        
        
        
        // TODO Auto-generated method stub

    }

    @Override
    protected int available() throws IOException {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    protected void bind(InetAddress host, int port) throws IOException {
        
        
        
        System.out.println("In SmartSocketImpl.bind(" + host + ", " + port + ")");
        // TODO Auto-generated method stub

    }

    @Override
    protected void close() throws IOException {
        // TODO Auto-generated method stub

    }

    @Override
    protected void connect(String host, int port) throws IOException {
        // TODO Auto-generated method stub

    }

    @Override
    protected void connect(InetAddress address, int port) throws IOException {
        // TODO Auto-generated method stub

    }

    @Override
    protected void connect(SocketAddress address, int timeout)
            throws IOException {
        // TODO Auto-generated method stub

    }

    @Override
    protected void create(boolean stream) throws IOException {
        // TODO Auto-generated method stub

    }

    @Override
    protected InputStream getInputStream() throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected OutputStream getOutputStream() throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected void listen(int backlog) throws IOException {
        // TODO Auto-generated method stub

    }

    @Override
    protected void sendUrgentData(int data) throws IOException {
        // TODO Auto-generated method stub

    }

    public Object getOption(int optID) throws SocketException {
        // TODO Auto-generated method stub
        return null;
    }

    public void setOption(int optID, Object value) throws SocketException {
        // TODO Auto-generated method stub

    }

}
