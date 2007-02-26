package test.virtual.address;

import smartsockets.virtual.VirtualSocketAddress;

public class ToBytes {

    public static void main(String [] args) { 
        
        try { 
            String serverString = "130.37.193.29-5555:3210";                                   
            
            VirtualSocketAddress one = new VirtualSocketAddress(serverString);                         
                                                                                            
            VirtualSocketAddress.fromBytes(one.toBytes(), 0);                     
            
        } catch (Exception e) {
            System.out.println("Oops: " + e);
            e.printStackTrace();
        }
    }
    
}
