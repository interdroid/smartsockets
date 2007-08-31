package ibis.smartsockets.plugin;

import ibis.smartsockets.virtual.InitializationException;
import ibis.smartsockets.virtual.VirtualSocket;
import ibis.smartsockets.virtual.VirtualSocketAddress;
import ibis.smartsockets.virtual.VirtualSocketFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Map;

import javax.net.SocketFactory;


public class SmartSocketFactory extends SocketFactory {

    private static SmartSocketFactory defaultFactory; 
    
    private VirtualSocketFactory factory;
    
    private SmartSocketFactory() throws InitializationException { 
        factory = VirtualSocketFactory.getDefaultSocketFactory();
    }
    
    @Override
    public Socket createSocket() throws IOException {
        return new SmartSocket();
    }
    
    @Override
    public Socket createSocket(String host, int port) throws IOException,
            UnknownHostException {
    
        VirtualSocketAddress a = new VirtualSocketAddress(host, port);
        return new SmartSocket(factory.createClientSocket(a, 0, null));
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        VirtualSocketAddress a = VirtualSocketAddress.partialAddress(host, port, port);
        return new SmartSocket(factory.createClientSocket(a, 0, null));
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localAddress, 
            int localPort) throws IOException, UnknownHostException {

        SmartSocket s = new SmartSocket();
        s.bind(new InetSocketAddress(localAddress, localPort));
        s.connect(new VirtualSocketAddress(host, port));
        return s;
    }

    @Override
    public Socket createSocket(InetAddress host, int port, 
            InetAddress localAddress, int localPort) throws IOException {
  
        SmartSocket s = new SmartSocket();
        s.bind(new InetSocketAddress(localAddress, localPort));
        s.connect(VirtualSocketAddress.partialAddress(host, port, port));
        return s;
    }

    protected VirtualSocket connect(VirtualSocketAddress target, int timeout, 
            Map<String, Object> properties) throws IOException {
        return factory.createClientSocket(target, timeout, properties);
    }
    
    public synchronized static SmartSocketFactory getDefault() { 
        
        if (defaultFactory == null) { 
            try { 
                defaultFactory = new SmartSocketFactory();
            } catch (InitializationException e) { 
                System.err.println("WARNING: failed to create " +
                        "SmartSocketFactory");
                e.printStackTrace(System.err);
                return null;
            }
        }
        
        return defaultFactory;
    }
}
