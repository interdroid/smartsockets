package ibis.smartsockets.discovery;


import ibis.smartsockets.util.NetworkUtils;
import ibis.util.ThreadPool;

import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Discovery {
    
    private static final Logger logger = 
            LoggerFactory.getLogger("ibis.smartsockets.discovery");
    
    protected static final int MAGIC = (0x42<<24 | 0xff<<16 | 0x42<<8 | 0xff);
    
    private Receiver receiver; 
    private Sender sender;
    private AnsweringMachine answer;
    
    private final int sendPort;
    private final int receivePort;
    private final int timeout;  
    
    public Discovery(int receivePort, int sendPort, int timeout) { 
        this.receivePort = receivePort;
        this.sendPort = sendPort;
        this.timeout = timeout;
    }
    
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
        
    public void advertise(String message) {
        InetAddress[] addresses = NetworkUtils.getAllHostAddresses();
                
        try {                        
            sender = new Sender(addresses, sendPort, receivePort, 
                    timeout, message); 
            sender.start();            
        } catch (Exception e) {
            logger.warn("Failed to create sender!", e);
        }
    }
            
    public void listnen(Callback callback) { 
    
        if (callback == null) { 
            throw new NullPointerException();
        }
        
        InetAddress[] addresses = NetworkUtils.getAllHostAddresses();
        
        try {                        
            receiver = new Receiver(addresses, receivePort, callback);
            receiver.start();
        } catch (Exception e) {
            logger.warn("Failed to create receiver!", e);
        }
    }
    
    public void answeringMachine(String prefix, String [] tags, String reply) { 
        
        try {
            answer = new AnsweringMachine(receivePort, prefix, tags, reply);            
            ThreadPool.createNew(answer, "discovery.AnsweringMachine");
        } catch (SocketException e) {
            logger.warn("Failed to create answering machine!", e);
        }
    }
        
    public String broadcastWithReply(String message) {
        
        try {
            SendReceive sr = new SendReceive(sendPort);            
            sr.setMessage(message, receivePort);
            
            long end = System.currentTimeMillis() + timeout;
            long left = timeout; 
            
            while (timeout == 0 || left > 0) {
                // Do a broadcast and hope we get a reply                
                sr.send((int)left);
                
                // Wait for the reply for 0.5 seconds.                
                String result = null; 
                
                try { 
                    result = sr.receive(500);
                } catch (SocketTimeoutException e) {
                    // ignore
                }
                
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
