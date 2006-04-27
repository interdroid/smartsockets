package ibis.connect.virtual.modules.routed;

import java.io.IOException;
import java.util.Map;

import ibis.connect.direct.SocketAddressSet;
import ibis.connect.virtual.ModuleNotSuitableException;
import ibis.connect.virtual.VirtualSocket;
import ibis.connect.virtual.VirtualSocketAddress;
import ibis.connect.virtual.modules.ConnectModule;
import ibis.connect.virtual.modules.direct.Direct;

public class Routed extends ConnectModule {

    private static final int DEFAULT_TIMEOUT = 4000;   
    
    private Direct direct;    
    
    protected Routed(String name) {
        super("ConnectModule(Routed)", true);
    }

    public void initModule() throws Exception {
        // nothing do do here.
    }

    public void startModule() throws Exception {

        if (serviceLink == null) {
            throw new Exception(name + ": no service link available!");       
        }
               
        direct = (Direct) parent.findModule("ConnectModule(Direct)");
        
        if (direct == null) {
            throw new Exception(name + ": no direct module available!");       
        }        
    }

    public SocketAddressSet getAddresses() {
        // TODO Auto-generated method stub
        return null;
    }

    public VirtualSocket connect(VirtualSocketAddress target, int timeout, 
            Map properties) throws ModuleNotSuitableException, IOException {

        if (timeout == 0 || timeout > DEFAULT_TIMEOUT) { 
            timeout = DEFAULT_TIMEOUT; 
        }
        
        return null;
    
    }

}
