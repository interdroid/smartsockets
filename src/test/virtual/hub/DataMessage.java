package test.virtual.hub;

import ibis.smartsockets.direct.DirectSocketAddress;
import ibis.smartsockets.hub.servicelink.ServiceLink;
import ibis.smartsockets.virtual.VirtualSocketFactory;

public class DataMessage  {
	
	private VirtualSocketFactory factory;
    
	private int repeat = 100;
	private int count = 100000;
	private int size = 8192;
	
	
    public DataMessage(VirtualSocketFactory factory) {
        this.factory = factory;
    }    
        
    public void run() { 
        
        try { 
        	ServiceLink sl = factory.getServiceLink();
        	
        	byte [] data = new byte[size];  
        	
        	DirectSocketAddress node = factory.getLocalHost();
        	DirectSocketAddress hub = factory.getLocalHub();

        	System.out.println("Sending to " + node + " @ " + hub);
        	
        	for (int r=0;r<repeat;r++) { 
        		long start = System.currentTimeMillis();

        		for (int i=0;i<count;i++) { 
        			sl.sendDataMessage(node, hub, null, data);
        		}

        		long end = System.currentTimeMillis();

        		double MBitsPersec = ((size*count*8000.0) / (end-start)) / (1000.0*1000.0); 
        		        		
        		System.out.println("Send " + (size*count) + " bytes in " + (end-start) + " ms. (" 
        				+ MBitsPersec + " MBit/s)");
        	}        	
        } catch (Exception e) {
            System.err.println("Oops: " + e);
            e.printStackTrace(System.err);
        } finally {
            try {
                factory.end();
            } catch (Exception e) {
                // ignore
            }
        }

        System.out.println("Done!");
    }
    
    public static void main(String[] args) {

        try { 
            VirtualSocketFactory factory = 
                VirtualSocketFactory.createSocketFactory((java.util.Properties) null, true);

            System.out.println("Created socket factory");
        
            new DataMessage(factory).run();
            
        } catch (Exception e) {
            System.err.println("Oops: " + e);
            e.printStackTrace(System.err);
        }
    }
}