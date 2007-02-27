package test.virtual.simple;


import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.SocketException;
import java.util.HashMap;

import smartsockets.virtual.InitializationException;
import smartsockets.virtual.VirtualServerSocket;
import smartsockets.virtual.VirtualSocket;
import smartsockets.virtual.VirtualSocketAddress;
import smartsockets.virtual.VirtualSocketFactory;

public class Latency {
    
    private static int DEFAULT_REPEAT = 10;
    private static int DEFAULT_COUNT = 10000;
    
    private static int TIMEOUT = 5000;
    
    private static int count = DEFAULT_COUNT;    
    private static int repeat = DEFAULT_REPEAT;
    
    private static VirtualSocketFactory sf;    
    private static HashMap<String, Object> connectProperties;
    
    private static void configure(VirtualSocket s) throws SocketException { 
 
        s.setSendBufferSize(1024*1024);
        s.setReceiveBufferSize(1024*1024);
        s.setTcpNoDelay(true);
    
        System.out.println("Configured socket: ");         
        System.out.println(" sendbuffer     = " + s.getSendBufferSize());
        System.out.println(" receiverbuffer = " + s.getReceiveBufferSize());
        System.out.println(" no delay       = " + s.getTcpNoDelay());        
    }
    
    public static void client(VirtualSocketAddress target) { 

        try { 
            VirtualSocket s = sf.createClientSocket(target, TIMEOUT, 
                    connectProperties);
            
            System.out.println("Created connection to " + target);
                                              
            configure(s);
                        
            DataInputStream in = new DataInputStream(s.getInputStream());
            DataOutputStream out = new DataOutputStream(s.getOutputStream());

            out.writeInt(count);
            out.writeInt(repeat);
            out.flush();                
            
            System.out.println("Starting test");
                                    
            for (int r=0;r<repeat;r++) {
                
                long time = System.currentTimeMillis();           
                                
                for (int i=0;i<count;i++) {
                    out.writeByte(42);
                    out.flush();
                    
                    in.readByte();
                }
                
                time = System.currentTimeMillis() - time;
            
                double rtt = (1.0*time) / count;  
                        
                System.out.println("Test took " + time + " ms. RTT = " 
                        + rtt + " ms.");
            }
            
            VirtualSocketFactory.close(s, out, in);
        } catch (Exception e) {
            System.out.println("Failed to create connection to " + target); 
        }
    }
    
    public static void server() throws IOException {
        
        System.out.println("Creating server");
        
        VirtualServerSocket ss = sf.createServerSocket(0, 0, connectProperties);
        
        System.out.println("Created server on " + ss.getLocalSocketAddress());
                    
        while (true) {
            System.out.println("Server waiting for connections"); 
            
            try { 
                VirtualSocket s = ss.accept();
                            
                System.out.println("Incoming connection from " 
                        + s.getRemoteSocketAddress());
            
                configure(s);
                
                DataInputStream in = new DataInputStream(s.getInputStream());
                DataOutputStream out = new DataOutputStream(s.getOutputStream());
                                           
                count = in.readInt();                              
                repeat = in.readInt();                              
                                
                System.out.println("Starting test rt " 
                        + count + " repeated " + repeat + " times."); 
                
                for (int r=0;r<repeat;r++) {                 
                    for (int i=0;i<count;i++) {
                        in.readByte();
                        out.writeByte(42);
                        out.flush();
                    }
                }

                System.out.println("done!"); 
                
                VirtualSocketFactory.close(s, out, in);
            } catch (Exception e) {
                System.out.println("Server got exception " + e); 
            }
        }
    }
    
    public static void main(String [] args) throws IOException { 
        
        try {
            sf = VirtualSocketFactory.createSocketFactory();
        } catch (InitializationException e1) {
            System.out.println("Failed to create socketfactory!");
            e1.printStackTrace();
            System.exit(1);
        }
        
        connectProperties = new HashMap<String, Object>();

        VirtualSocketAddress target = null;

        for (int i=0;i<args.length;i++) { 
            if (args[i].equals("-target")) {                                 
                target = new VirtualSocketAddress(args[++i]);
            } else if (args[i].equals("-repeat")) {                                 
                repeat = Integer.parseInt(args[++i]);  
            } else if (args[i].equals("-count")) {                                 
                count = Integer.parseInt(args[++i]);      
            } else { 
                System.err.println("Unknown option: " + args[i]);                
            }
        }

        if (target == null) { 
            server();
        } else { 
            client(target);
        }
    }
}
