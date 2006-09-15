package ibis.connect.discovery;

import ibis.connect.util.NetworkUtils;

import java.net.InetAddress;
import java.net.SocketException;

import org.apache.log4j.Logger;

public class Discovery {
    
    protected static Logger logger = 
            ibis.util.GetLogger.getLogger(Discovery.class.getName());
    
    private static final int DEFAULT_SEND_PORT = 24545;
    private static final int DEFAULT_RECEIVE_PORT = 24454;
    private static final int DEFAULT_SLEEP = 5;  
    
    protected static final int MAGIC = (0x42 << 24 | 0xff << 16 | 0x42 << 8 | 0xff);
    
    private static Receiver receiver; 
    private static Sender sender;
    private static AnsweringMachine answer;
        
    protected static void write(byte [] buffer, int pos, int value) {         
        buffer[pos]   = (byte)(0xff & (value >> 24));
        buffer[pos+1] = (byte)(0xff & (value >> 16));
        buffer[pos+2] = (byte)(0xff & (value >>  8));
        buffer[pos+3] = (byte)(0xff & value);                       
    }
    
    protected static int read(byte [] buffer, int pos) { 
        return (((buffer[pos] & 0xff) << 24)   | 
                ((buffer[pos+1] & 0xff) << 16) |
                ((buffer[pos+2] & 0xff) << 8)  | 
                 (buffer[pos+3] & 0xff));
    }
        
    public static void advertise(int port, int sleep, String message) {
        if (port <= 0) { 
            port = DEFAULT_SEND_PORT;           
        }
        
        if (sleep <= 0) { 
            sleep = DEFAULT_SLEEP * 1000;           
        }
       
        InetAddress[] addresses = NetworkUtils.getAllHostAddresses();
                
        try {                        
            sender = new Sender(addresses, port, DEFAULT_RECEIVE_PORT, 
                    sleep, message); 
            sender.start();            
        } catch (Exception e) {
            logger.warn("Failed to create sender!", e);
        }
    }
            
    public static void listnen(int port, Callback callback) { 
    
        if (callback == null) { 
            throw new NullPointerException();
        }

        if (port <= 0) { 
            port = DEFAULT_RECEIVE_PORT;
        }
        
        InetAddress[] addresses = NetworkUtils.getAllHostAddresses();
        
        try {                        
            receiver = new Receiver(addresses, port, callback);
            receiver.start();
        } catch (Exception e) {
            logger.warn("Failed to create receiver!", e);
        }
    }
    
    public static void answeringMachine(int port, String prefix, String reply) { 
        
        if (port == 0) { 
            port = DEFAULT_RECEIVE_PORT;
        }
        
        try {
            answer = new AnsweringMachine(port, prefix, reply);
            answer.start();
        } catch (SocketException e) {
            logger.warn("Failed to create answering machine!", e);
        }
    }
        
    public static String broadcastWithReply(String message, int destport, 
            int timeout) {
        
        InetAddress[] ads = NetworkUtils.getAllHostAddresses();
                
        if (destport == 0) { 
            destport = DEFAULT_RECEIVE_PORT;
        }
        
        try {
            SendReceive sr = new SendReceive(0);            
            sr.setMessage(message, destport);
            
            long end = System.currentTimeMillis() + timeout;
            long left = timeout; 
            
            while (timeout == 0 || left > 0) {
                // Do a broadcast and hope we get a reply                
                sr.send((int)left);
                
                // Wait for the reply for 0.5 seconds.
                String result = sr.receive(500);
                
                // If we get a reply we're done!
                if (result != null) { 
                    return result;
                }
                
                // Otherwise, we make sure we haven't timed out yet, and 
                // try again 
                if (timeout > 0) {
                    left = end - System.currentTimeMillis();
                }
            } 
            
        } catch (Exception e) { 
            logger.warn("Failed to perform broadcastWithReply!", e);            
        }
        
        return null;
    }
}
