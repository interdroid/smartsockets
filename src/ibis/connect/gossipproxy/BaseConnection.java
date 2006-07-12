package ibis.connect.gossipproxy;

import ibis.connect.direct.DirectSocket;
import ibis.connect.direct.DirectSocketFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;

import org.apache.log4j.Logger;

public abstract class BaseConnection implements Runnable {

    protected static Logger logger = 
        ibis.util.GetLogger.getLogger(BaseConnection.class.getName()); 
    
    protected final DirectSocket s;
    protected final DataInputStream in;
    protected final DataOutputStream out; 
    
    protected Connections connections;
    protected final ProxyList knownProxies; 
          
    protected BaseConnection(DirectSocket s, DataInputStream in, 
            DataOutputStream out, Connections connections, ProxyList proxies) {
        
        this.s = s;
        this.in = in;
        this.out = out;
        this.connections = connections;
        this.knownProxies = proxies;
    }
        
    public void activate() {
        // TODO: Use pool ?         
        new Thread(this, getName()).start();
    }
        
    public void run() { 

        boolean cont = true; 
        
        while (cont) {     
            cont = runConnection();
        } 
        
        // NOTE: Do NOT close the socket here, since it may still be in use!
        // DirectSocketFactory.close(s, out, in);        
    }
    
    protected abstract boolean runConnection();    
    protected abstract String getName();           
}
