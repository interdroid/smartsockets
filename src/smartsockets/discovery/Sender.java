package smartsockets.discovery;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import org.apache.log4j.Logger;

public class Sender extends Thread {
    
    private static final Logger logger = 
        ibis.util.GetLogger.getLogger("smartsockets.discovery");
    
    private final int sleep;
    
    private final DatagramSocket [] sockets; 
    private final InetSocketAddress target;    
    private final DatagramPacket packet; 
    
    protected Sender(InetAddress [] ads, int sendport, int receiveport, 
            int sleep, String message) throws Exception {         
        
        super("MulticastSender");
                        
        this.sleep = sleep;

        byte [] data = message.getBytes();
                        
        if (data.length > 1024) { 
            throw new IllegalArgumentException("Message exceeds 1024 bytes!");
        }
                
        byte [] tmp = new byte[data.length+8];
        
        Discovery.write(tmp, 0, Discovery.MAGIC);
        Discovery.write(tmp, 4, data.length);
        
        System.arraycopy(data, 0, tmp, 8, data.length);
        
        target = new InetSocketAddress("255.255.255.255", receiveport);        
        packet = new DatagramPacket(tmp, tmp.length, target);
        
        //this.sockets = createSockets(ads, port);
        
        sockets = new DatagramSocket[1];
        sockets[0] = new DatagramSocket(sendport);        
    }
    
    /*
    private DatagramSocket [] createSockets(InetAddress [] addresses, int port)
        throws Exception {
               
        int size = addresses.length;
        
        DatagramSocket [] tmp = new DatagramSocket[size];
        Throwable [] exceptions = new Throwable[size];
        
        for (int i=0;i<addresses.length;i++) {
            try { 
                tmp[i] = create(addresses[i], port+1);
            } catch (Throwable t) {
                exceptions[i] = t;
            }
            
            if (tmp[i] == null) { 
                size--;
            }
        }
        
        if (size == 0) { 
            
            StringBuffer buf = new StringBuffer("Failed to create any " 
                    + "DatagramSockets!\n");
            
            for (int i=0;i<exceptions.length;i++) {
                if (exceptions[i] != null) {
                    buf.append(exceptions[i].getLocalizedMessage());  
                    buf.append("\n");
                }
            }
            
            throw new Exception(buf.toString());
        }
        
        DatagramSocket [] result = new DatagramSocket[size];
        
        for (int i=0;i<tmp.length;i++) { 
            if (tmp[i] != null) { 
                result[--size] = tmp[i];
            }
        }
               
        return result;
    }
    
    private DatagramSocket create(InetAddress address, int port) 
                                                       throws SocketException { 
        if (logger.isInfoEnabled()) { 
            logger.info("Creating port " + address + ":" + port);
        }
        
        if (!address.isLoopbackAddress()) {            
            DatagramSocket socket = 
                new DatagramSocket(new InetSocketAddress(address, port));
            socket.setSoTimeout(sleep);
            socket.setBroadcast(true);
            return socket;
        } 
        
        return null;     
    }
    */
    
    public void send() { 
     
        if (packet == null) {
            return;
        }
        
        for (int i=0;i<sockets.length;i++) { 
            try {
                if (logger.isInfoEnabled()) {
                    logger.info("MulticastSender sending data "
                                + packet.getSocketAddress() + " " 
                                + packet);
                }                
                if (packet != null) {                         
                    sockets[i].send(packet);
                }
            } catch (Exception e) {
                if (logger.isInfoEnabled()) {
                    logger.info("MulticastSender got exception ", e);
                }
            }                
        }            
    }
    
    public void run() { 
     
        while (true) { 
        
            long time = System.currentTimeMillis();
            
            send();
            
            time = System.currentTimeMillis() - time;
            
            if (time < sleep) { 
                try { 
                    sleep(sleep-time);
                } catch (InterruptedException e) {
                    // ignore 
                }
            }            
        }        
    }    
}
