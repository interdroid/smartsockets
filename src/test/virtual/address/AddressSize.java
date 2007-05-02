package test.virtual.address;

import ibis.smartsockets.virtual.VirtualServerSocket;
import ibis.smartsockets.virtual.VirtualSocketAddress;
import ibis.smartsockets.virtual.VirtualSocketFactory;

public class AddressSize {

    public static void main(String [] args) { 
        
        try { 
            VirtualSocketFactory f = VirtualSocketFactory.createSocketFactory();        
            VirtualServerSocket s = f.createServerSocket(8899, 1, null);            
            VirtualSocketAddress a = s.getLocalSocketAddress();
            
            System.out.println("Address  : " + a);
            System.out.println("Codedform: " + a.toBytes().length);
            
        } catch (Exception e) {
            System.err.println("Oops: " + e);
        }
    }
    
}
