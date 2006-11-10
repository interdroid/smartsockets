package smartsockets.discovery;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import org.apache.log4j.Logger;

class Receiver extends Thread {
    
    private static final Logger logger = 
        ibis.util.GetLogger.getLogger("smartsockets.discovery");
    
    private DatagramSocket socket; 
    private DatagramPacket packet; 
    
    private final Callback callback;
    private final InetAddress[] addresses;
    private final int timeout;
    
    Receiver(InetAddress [] ads, int port, Callback callback) 
        throws SocketException {
        this(ads, port, callback, 0);
    }
    
    Receiver(InetAddress [] ads, int port, Callback callback, int timeout) 
        throws SocketException {
        
        super("discovery.Receiver");
    
        this.addresses = ads;
        this.callback = callback;
        this.timeout = timeout;
        
        if (port == 0) {         
            socket = new DatagramSocket();
        } else { 
            socket = new DatagramSocket(port);            
        }
        
        packet = new DatagramPacket(new byte[64*1024], 64*1024);
    }
             
    public String receive(long timeout) throws IOException { 

        long end = System.currentTimeMillis() + timeout;
        long left = timeout; 

        while (timeout == 0 || left > 0) { 
            if (logger.isInfoEnabled()) { 
                logger.info("Receiver waiting for data");
            }
        
            socket.receive(packet);
                
            byte [] tmp = packet.getData();

            if (tmp.length > 8) {
                if (Discovery.read(tmp, 0) != Discovery.MAGIC) {
                    if (logger.isInfoEnabled()) { 
                        logger.info("Discarding packet, wrong MAGIC");
                    }
                } else {             
                    int len = Discovery.read(tmp, 4);
                    
                    if (logger.isInfoEnabled()) { 
                        logger.info("MAGIC OK, data length = " + len);
                    }
                                
                    if (len > 1024) {
                        if (logger.isInfoEnabled()) { 
                            logger.info("Discarding packet, wrong size");
                        }
                    } else { 
                        byte [] data = new byte[len];
                        System.arraycopy(tmp, 8, data, 0, len);        
                        return new String(data); 
                    }                                               
                }
            }
            
            if (timeout > 0) {
                left = end - System.currentTimeMillis();
            }
        }
        
        throw new SocketTimeoutException();
    }    
    
    public void run() {
    
        long end = System.currentTimeMillis() + timeout;
        long left = timeout; 
        
        while (timeout == 0 || left > 0) {                
            
            try {
                String message = receive(left);

                boolean cont = callback.gotMessage(message);
                
                if (!cont) {
                    // the callback has intstrcted us to quit.
                    return;
                }
             
                if (timeout > 0) {
                    left = end - System.currentTimeMillis();
                }                
            } catch (SocketTimeoutException te) { 
                // ignore
            } catch (Exception e) {
                if (logger.isInfoEnabled()) { 
                    logger.info("Receiver failed!", e);
                }
            }
        }
    }
}
