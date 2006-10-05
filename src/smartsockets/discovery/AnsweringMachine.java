 package smartsockets.discovery;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

public class AnsweringMachine extends Thread {

    private final DatagramSocket socket; 
    private final DatagramPacket packet; 
    private final DatagramPacket replyPacket; 
        
    private final String prefix;
          
    AnsweringMachine(int port, String prefix, String reply) throws SocketException {
        super("discovery.AnsweringMachine");
            
        this.prefix = prefix;
        
        if (port == 0) {         
            socket = new DatagramSocket();
        } else { 
            socket = new DatagramSocket(port);            
        }
    
        packet = new DatagramPacket(new byte[64*1024], 64*1024);
        
        byte [] data = reply.getBytes();
        
        if (data.length > 1024) { 
            throw new IllegalArgumentException("Reply exceeds 1024 bytes!");
        }
                
        byte [] tmp = new byte[data.length+8];
        
        Discovery.write(tmp, 0, Discovery.MAGIC);
        Discovery.write(tmp, 4, data.length);
        
        System.arraycopy(data, 0, tmp, 8, data.length);
        
        replyPacket = new DatagramPacket(tmp, tmp.length);
       
    }
    
    private String parseMessage() { 

        byte [] tmp = packet.getData();

        if (tmp.length > 8) {
            if (Discovery.read(tmp, 0) != Discovery.MAGIC) {
                Discovery.logger.info("Discarding packet, wrong MAGIC"); 
            } else {             
                int len = Discovery.read(tmp, 4);
        
                Discovery.logger.info("MAGIC OK, data length = " + len);
                            
                if (len > 1024) {
                    Discovery.logger.info("Discarding packet, wrong size");
                } else { 
                    byte [] data = new byte[len];
                    System.arraycopy(tmp, 8, data, 0, len);        
                    return new String(data); 
                }                                               
            }
        }
        
        return null;
    }    
    
    private void sendReply() { 
        
        try { 
            replyPacket.setSocketAddress(packet.getSocketAddress());
            
            Discovery.logger.info("AnsweringMachine sending reply to " 
                    + replyPacket.getSocketAddress().toString());
                        
            socket.send(replyPacket);
        } catch (IOException e) { 
            Discovery.logger.warn("Failed to send reply", e);               
        }        
    }
   
    public void run() {

        Discovery.logger.info("AnsweringMachine waiting for calls...");
        
        while (true) {
            try { 

                // Wait for a packet.
                socket.receive(packet);

                // Try to extract the message 
                String result = parseMessage();

                Discovery.logger.info("AnsweringMachine got message: \"" 
                        + result + "\" from " 
                        + packet.getSocketAddress().toString());
                
                // If we have received a message, and it starts with the right 
                // prefix, we send a reply.               
                if (result != null && result.startsWith(prefix)) { 
                    sendReply();                
                }
            } catch (Exception e) {
                Discovery.logger.warn("Failed to receive packet", e);
            }
        }   
    }
}
