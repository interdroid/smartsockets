package ibis.connect.virtual.modules.splice;

import java.io.IOException;
import java.util.Map;

import ibis.connect.direct.DirectSocketFactory;
import ibis.connect.direct.SocketAddressSet;
import ibis.connect.virtual.ModuleNotSuitableException;
import ibis.connect.virtual.VirtualSocket;
import ibis.connect.virtual.VirtualSocketAddress;
import ibis.connect.virtual.modules.ConnectModule;
import ibis.connect.virtual.modules.direct.Direct;

public class Splice extends ConnectModule {
   
    private DirectSocketFactory direct;
       
    public Splice() {
        super("ConnectModule(Splice)", true);
    }
        
    public VirtualSocket connect(VirtualSocketAddress target, int timeout, Map properties) throws ModuleNotSuitableException, IOException {
        // TODO Auto-generated method stub
        return null;
    }

    public SocketAddressSet getAddresses() {
        // Nothing to do here...
        return null;
    }

    public void initModule() throws Exception {
        // Create a direct socket factory.
        direct = DirectSocketFactory.getSocketFactory();
        
    }

    public boolean matchAdditionalRequirements(Map requirements) {
        // Alway match the requirements ....
        return true;
    }

    public void startModule() throws Exception {
        if (serviceLink == null) {
            throw new Exception(name + ": no service link available!");       
        }
        
       // direct = (Direct) parent.findModule("ConnectModule(Direct)");
        
      //  if (direct == null) {
      //      throw new Exception(name + ": no direct module available!");       
       // }        
        // TODO Auto-generated method stub
        
    }

}
