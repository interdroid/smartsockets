package ibis.connect.discovery;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

class Receiver extends Thread {

    private DatagramSocket socket; 
    private DatagramPacket packet; 
    
    private final Callback callback;
    private final InetAddress[] addresses;
    
    Receiver(InetAddress [] ads, int port, Callback callback) 
        throws SocketException {
        
        super("discovery.Receiver");
    
        this.addresses = ads;
        this.callback = callback;
        
        socket = new DatagramSocket(port);
        packet = new DatagramPacket(new byte[64*1024], 64*1024);
    }
      
    private boolean isLocal(InetAddress address) { 
        
        for (int i=0;i<addresses.length;i++) { 
            if (address.equals(addresses[i])) { 
                return true;
            }
        }
        
        return false;
    }
        
    public void run() {
    
        boolean cont = true;
        
        while (cont) {                
            try {
                Discovery.logger.info("Receiver waiting for data");
                
                socket.receive(packet);
                        
                byte [] tmp = packet.getData();

                if (tmp.length > 8) {
    
                    if (Discovery.read(tmp, 0) != Discovery.MAGIC) {
                        Discovery.logger.info("Discarding packet, wrong MAGIC"); 
                        break;                        
                    }
                    
                    int len = Discovery.read(tmp, 4);
                    
                    Discovery.logger.info("MAGIC OK, data length = " + len);
                                        
                    if (len > 1024) {
                        Discovery.logger.info("Discarding packet, wrong size");
                        break;
                    }
                        
                    byte [] data = new byte[len];
                    System.arraycopy(tmp, 8, data, 0, len);
                
                    // Received packet     
                    cont = callback.gotMessage(new String(data));
                }                                               
            } catch (Exception e) {
                Discovery.logger.info("Receiver failed!", e);
            }
        }
    }
}
