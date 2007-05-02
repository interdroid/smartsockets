package test.virtual.address;

import ibis.smartsockets.virtual.VirtualSocketAddress;

public class ReadAddress {

    public static void main(String [] args) { 
    
        System.out.println("Reading " + args.length + " addresses:");
        
        for (int i=0;i<args.length;i++) {
            
            System.out.print(" " + args[i] + " -> ");
                        
            try {                
                new VirtualSocketAddress(args[i]);                
                System.out.println(" ok");                                
            } catch (Exception e) {
                System.out.println(" failed!");                
                e.printStackTrace();              
            }
        }
    } 
}
