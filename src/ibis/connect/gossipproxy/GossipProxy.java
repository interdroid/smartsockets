package ibis.connect.gossipproxy;

import ibis.connect.virtual.VirtualSocketAddress;
import ibis.connect.virtual.VirtualSocketFactory;

import java.io.IOException;

import org.apache.log4j.Logger;

public class GossipProxy {
    
    protected static Logger logger = 
        ibis.util.GetLogger.getLogger(GossipProxyClient.class.getName());
               
    private ProxyList proxies;     
    private ProxyAcceptor proxyAcceptor;
    private ProxyConnector proxyConnector;
                
    public GossipProxy() throws IOException { 

        VirtualSocketFactory factory = VirtualSocketFactory.getSocketFactory();
        
        // Create the proxy list
        proxies = new ProxyList();
                
        proxyAcceptor = new ProxyAcceptor(this, proxies, factory);        
        proxyConnector = new ProxyConnector(this, proxies, factory);
        
        VirtualSocketAddress local = proxyAcceptor.getLocal();         
        
        proxyConnector.setLocal(local);
                
        // Create a description for the local machine. 
        ProxyDescription localDesc = new ProxyDescription(local, null, 0, 0);        
        localDesc.setReachable(1, 1, ProxyDescription.DIRECT);
        localDesc.setCanReachMe(1, 1);
        
        proxies.addLocalDescription(localDesc);
    }
    
    ProxyDescription getProxyDescription(VirtualSocketAddress a) {
        return getProxyDescription(a, 0, null, false);        
    }
    
    ProxyDescription getProxyDescription(VirtualSocketAddress a, 
            boolean direct) {
        return getProxyDescription(a, 0, null, direct);        
    }
        
    synchronized ProxyDescription getProxyDescription(VirtualSocketAddress a, 
            int state, VirtualSocketAddress src, boolean direct) { 
        
        ProxyDescription tmp = proxies.get(a);
        
        if (tmp == null) { 
            tmp = new ProxyDescription(a, src, proxies.size()+1, state);
            proxies.add(tmp);                
            proxyConnector.addNewProxy(tmp);
        }
        
        if (direct && !tmp.canDirectlyReachMe()) { 
            tmp.setCanReachMe(proxies.size(), state);
        }        
        
        return tmp;
    }
    
    /*
    public synchronized void addClient(VirtualSocketAddress address) {         
        // TODO: Check if we can actually reach the client directly ? 
        localDescription.addClient(address);                
    }
    */
    
    void activateConnection(ProxyConnection c) {
        // TODO: Should use threadpool
        new Thread(c).start();
    }
}
