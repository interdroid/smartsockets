package test.plain;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

public class RoutedThroughput {

    // All field are initilized with their default values
    private static int rcvBufPre = -1;
    private static int sndBufPre = -1;
    
    private static int rcvBufPost = -1;
    private static int sndBufPost = -1;
    
    private static int repeat = 10;        
    private static int count = 100;
    private static int size = 1024*1024;
    private static int port = 8899;
    
    private static InetSocketAddress target = null;
    
    private static Socket sender;
    private static DataOutputStream senderOut;
    private static DataInputStream senderIn;
    
    private static Socket receiver;
    private static DataOutputStream receiverOut;
    private static DataInputStream receiverIn;
    
    private static ServerSocket server;
    
    private static void configure(Socket s, int rcvbuf, int sndbuf) throws SocketException {     
        
        if (rcvbuf > 0)        
            s.setReceiveBufferSize(rcvbuf);
        
        if (sndbuf > 0)        
            s.setSendBufferSize(sndbuf);
        
        s.setTcpNoDelay(true);
        s.setTrafficClass(0x08);
    } 
        
    private static void print(Socket s) throws SocketException { 
        System.out.println("Configured socket: ");         
        System.out.println(" sendbuffer    = " + s.getSendBufferSize());
        System.out.println(" receivebuffer = " + s.getReceiveBufferSize());
        System.out.println(" no delay      = " + s.getTcpNoDelay());        
    }
  
    private static void connect() throws IOException { 
   
        sender = new Socket();
        sender.setReuseAddress(true);        
        
        configure(sender, rcvBufPre, sndBufPre);
        print(sender);

        sender.connect(target);                    
       
        configure(sender, rcvBufPost, rcvBufPre);
        print(sender);
        
        senderOut = new DataOutputStream(sender.getOutputStream());
        senderIn = new DataInputStream(sender.getInputStream());
    }
    
    private static void close() throws IOException { 
        
        if (sender != null) { 
            senderOut.close();
            senderIn.close();
            sender.close();   
        }
        
        if (receiver != null) { 
            receiverOut.close();
            receiverIn.close();
            receiver.close();   
        }
       
        if (server != null) {
            server.close();
        }
    }
    
    private static void accept() throws IOException {
        
        if (server == null) { 
            System.out.println("Creating server socket");

            server = new ServerSocket();
            
            if (rcvBufPre > 0) {      
                server.setReceiveBufferSize(rcvBufPre);
            }
            
            server.bind(new InetSocketAddress(port), 100);
  
            System.out.println("Created server on " + server.toString());
        }
        
        receiver = server.accept();
        
        configure(receiver, rcvBufPost, sndBufPost);
        print(receiver);
        
        receiverOut = new DataOutputStream(receiver.getOutputStream());
        receiverIn = new DataInputStream(receiver.getInputStream());
    }
     
    private static void sender() throws IOException { 
        System.out.println("Sender creating connection to " + target);
        
        connect();
                
        senderOut.writeInt(count);
        senderOut.writeInt(repeat);
        senderOut.writeInt(size);
        senderOut.flush();

        byte [] data = new byte[size]; 
        
        for (int r = 0; r < repeat; r++) {
            
            long time = System.currentTimeMillis();
        
            for (int c = 0; c < count; c++) {
                senderOut.write(data);
                senderOut.flush();
            }
            
            senderIn.read();
            
            time = System.currentTimeMillis() - time;

            double tp = (1000.0 * size * count) / (1024.0*1024.0*time);  
            double mbit = (8000.0 * size * count) / (1024.0*1024.0*time);  
                    
            System.out.println("Test took " + time + " ms. Throughput = " 
                    + tp + " MByte/s (" + mbit + " MBit/s)");
        }
        
        System.out.println("Sender autotuning resulted in:");
        print(sender);
        
        // Handshake to ensure we don't close too early...
        senderOut.write(42);
        senderIn.read();
    }
    
    private static void receiver() throws IOException { 
        
        accept();

        count = receiverIn.readInt();
        repeat = receiverIn.readInt();
        size = receiverIn.readInt();    
            
        byte [] data = new byte[size];
            
        for (int r = 0; r < repeat; r++) {
            for (int c = 0; c < count; c++) {
                receiverIn.readFully(data);
            }
            
            receiverOut.write(42);
            receiverOut.flush();
        }

        System.out.println("Receiver autotuning resulted in:");
        print(receiver);
        
        // Handshake to ensure we don't close too early...
        receiverIn.read();
        receiverOut.write(42);
    }
    
    private static void router() throws IOException { 
        
        if (target == null) { 
            System.err.println("Router requires target!");
            System.exit(1);
        }
       
        // Connect to receiver (or next router)
        connect();
        
        // Accept from sender (or previous router) 
        accept();

        // Read parameters
        count = receiverIn.readInt();
        repeat = receiverIn.readInt();
        size = receiverIn.readInt();    
            
        // Forward parameters to next machine
        senderOut.writeInt(count);
        senderOut.writeInt(repeat);
        senderOut.writeInt(size);
        senderOut.flush();
        
        byte [] data = new byte[size];
        
        // Route the data
        for (int r = 0; r < repeat; r++) {
            
            for (int c = 0; c < count; c++) {
                receiverIn.readFully(data);
                senderOut.write(data);
                senderOut.flush();
            }
            
            int ack = senderIn.read();
            receiverOut.write(ack);
            receiverOut.flush();
        }

        System.out.println("Router autotuning resulted in (sender):");
        print(sender);
        
        System.out.println("Router autotuning resulted in (receiver):");
        print(receiver);
        
        // Handshake to ensure we don't close too early...
        senderIn.read();
        receiverOut.write(42);

        receiverIn.read();
        senderOut.write(42);
    }
    
    public static void main(String[] args) {

        boolean router = false;
        
        for (int i=0;i<args.length;i++) { 
            if (args[i].equalsIgnoreCase("-repeat")) { 
                repeat = Integer.parseInt(args[i+1]);
                args[i+1] = null;
                args[i] = null;
                i++;
                
            } else if (args[i].equalsIgnoreCase("-count")) { 
                count = Integer.parseInt(args[i+1]);
                args[i+1] = null;
                args[i] = null;
                i++;
        
            } else if (args[i].equalsIgnoreCase("-size")) { 
                size = Integer.parseInt(args[i+1]);
                args[i+1] = null;
                args[i] = null;
                i++;
        
            } else if (args[i].equalsIgnoreCase("-port")) { 
                port = Integer.parseInt(args[i+1]);
                args[i+1] = null;
                args[i] = null;
                i++;
        
            } else if (args[i].equalsIgnoreCase("-buffers")) { 
                rcvBufPost = sndBufPost = Integer.parseInt(args[i+1]);
                args[i+1] = null;
                args[i] = null;
                i++;
                
            } else if (args[i].equalsIgnoreCase("-bufferspre")) { 
                rcvBufPre = sndBufPre = Integer.parseInt(args[i+1]);
                args[i+1] = null;
                args[i] = null;
                i++;
                
            } else if (args[i].equalsIgnoreCase("-rcvbufpost")) { 
                rcvBufPost = Integer.parseInt(args[i+1]);
                args[i+1] = null;
                args[i] = null;
                i++;
                
            } else if (args[i].equalsIgnoreCase("-sndbufpost")) { 
                sndBufPost = Integer.parseInt(args[i+1]);
                args[i+1] = null;
                args[i] = null;
                i++;
       
            } else if (args[i].equalsIgnoreCase("-rcvbufpre")) { 
                rcvBufPre = Integer.parseInt(args[i+1]);
                args[i+1] = null;
                args[i] = null;
                i++;
                
            } else if (args[i].equalsIgnoreCase("-sndbufpre")) { 
                sndBufPre = Integer.parseInt(args[i+1]);
                args[i+1] = null;
                args[i] = null;
                i++;
                
            } else if (args[i].equalsIgnoreCase("-target")) { 
                target = new InetSocketAddress(args[i+1], 
                        Integer.parseInt(args[i+2]));
                
                args[i+2] = null;
                args[i+1] = null;
                args[i] = null;
                i += 2;
                
            } else if (args[i].equalsIgnoreCase("-router")) { 
                router = true;
                args[i] = null;
 
            } else { 
                System.err.println("Unknown option: " + args[i]);
                System.exit(1);
            }
        }
 
        try {
            
            if (router) { 
                router();
            } else if (target != null) { 
                sender();
            } else { 
                receiver();
            }

            close();
            
        } catch (Exception e) {
            System.out.println("EEK!");
            e.printStackTrace(System.err);
        } finally { 
            try {
                close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
;