package smartsockets;

import java.net.SocketImpl;
import java.net.SocketImplFactory;
import java.util.Map;

import smartsockets.virtual.InitializationException;
import smartsockets.virtual.VirtualSocketFactory;

//INITIAL IMPLEMENTATION -- DO NOT USE!!

public class SmartSocketImplFactory implements SocketImplFactory {
    
    private VirtualSocketFactory factory;
    
    public SmartSocketImplFactory() throws InitializationException {   
        factory = VirtualSocketFactory.createSocketFactory();
    }
    
    public SmartSocketImplFactory(Map properties, boolean addDefaults) 
        throws InitializationException {   
        factory = VirtualSocketFactory.createSocketFactory(properties, addDefaults);
    }
    
    public SmartSocketImplFactory(java.util.Properties properties, 
            boolean addDefaults) throws InitializationException {
        factory = VirtualSocketFactory.createSocketFactory(properties, addDefaults);
    }
    
    public SocketImpl createSocketImpl() {
        return new SmartSocketImpl(this);
    } 
}
