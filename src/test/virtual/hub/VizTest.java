package test.virtual.hub;

import ibis.smartsockets.direct.DirectSocketAddress;
import ibis.smartsockets.virtual.VirtualSocketFactory;

import java.io.IOException;
import java.util.Arrays;

public class VizTest {

	private VirtualSocketFactory factory;
	private int state = 0;
	private boolean done = false;

	public VizTest(VirtualSocketFactory factory) throws IOException {

		this.factory = factory;

	//	factory.getServiceLink().registerProperty("smartsockets.viz", 
	//			"S^state=" + state++);
		
		System.out.println("##### VIZ TEST START #####");
		
		
		boolean ok = factory.getServiceLink().registerProperty("smartsockets.viz", "I^" + "ibis0"
                  + "," + "@little@house@ontheprairy");
	
		if (!ok) { 
			System.out.println("EEP: registration failed!");
		}
	
	}    

	public synchronized void done() {
		done = true;
	}

	public synchronized boolean getDone() {
		return done;
	}

	public void run() { 

		try { 
			while (!getDone()) { 
				DirectSocketAddress[] hubs = factory.getKnownHubs();

				System.out.println("Known hubs: " + Arrays.deepToString(hubs));

				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					// ignore
				}

	//			factory.getServiceLink().updateProperty("smartsockets.viz", 
	//					"S^state=" + state++);
	
				String tmp = "I^" + "ibis" + state++  + "," + "@little@house@ontheprairy";
				
				System.out.println("PROP: " + tmp);
				
				boolean ok = factory.getServiceLink().updateProperty("smartsockets.viz", tmp);

				if (!ok) { 
					System.out.println("EEP: update failed!");
				}
						
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
				VirtualSocketFactory.createSocketFactory(null, true);

			System.out.println("Created socket factory");

			new VizTest(factory).run();

		} catch (Exception e) {
			System.err.println("Oops: " + e);
			e.printStackTrace(System.err);
		}
	}
}
