package test.virtual.simple;

import ibis.connect.virtual.VirtualServerSocket;
import ibis.connect.virtual.VirtualSocket;
import ibis.connect.virtual.VirtualSocketAddress;
import ibis.connect.virtual.VirtualSocketFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.SocketException;
import java.util.HashMap;

public class Throughput {
    
    private static int DEFAULT_REPEAT = 10;
    private static int DEFAULT_COUNT = 100;
    
    private static int TIMEOUT = 5000;
    private static int DEFAULT_SIZE = 1024*1024;
    
    private static int count = DEFAULT_COUNT;    
    private static int repeat = DEFAULT_REPEAT;
    private static int size = DEFAULT_SIZE;
    
    private static VirtualSocketFactory sf;    
    private static HashMap connectProperties;
    
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

            out.writeInt(size);
            out.writeInt(count);
            out.writeInt(repeat);
            out.flush();                
            
            byte [] data = new byte[size];
            
            System.out.println("Starting test");
                                    
            for (int r=0;r<repeat;r++) {
                
                long time = System.currentTimeMillis();           
                                
                for (int i=0;i<count;i++) {
                    out.write(data);
                    out.flush();
                }
                
                in.read();
            
                time = System.currentTimeMillis() - time;
            
                double tp = (1000.0 * size * count) / (1024.0*1024.0*time);  
                double mbit = (8000.0 * size * count) / (1024.0*1024.0*time);  
                        
                System.out.println("Test took " + time + " ms. Throughput = " 
                        + tp + " MByte/s (" + mbit + " MBit/s)");
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
                                           
                size = in.readInt();
                count = in.readInt();                              
                repeat = in.readInt();                              
                                
                byte [] data = new byte[size];
                
                System.out.println("Starting test byte[" + size + "] x " 
                        + count + " repeated " + repeat + " times."); 
                
                for (int r=0;r<repeat;r++) {                 
                    for (int i=0;i<count;i++) {
                        in.readFully(data);
                    }
                
                    out.write((byte) 42); 
                    out.flush();
                }

                System.out.println("done!"); 
                
                VirtualSocketFactory.close(s, out, in);
            } catch (Exception e) {
                System.out.println("Server got exception " + e); 
            }
        }
    }
    
    public static void main(String [] args) throws IOException { 
        
        sf = VirtualSocketFactory.getSocketFactory();
        
        connectProperties = new HashMap();

        VirtualSocketAddress target = null;

        for (int i=0;i<args.length;i++) { 
            if (args[i].equals("-target")) {                                 
                target = new VirtualSocketAddress(args[++i]);
            } else if (args[i].equals("-size")) {                                 
                size = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-repeat")) {                                 
                repeat = Integer.parseInt(args[++i]);            
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
