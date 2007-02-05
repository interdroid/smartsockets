package test.virtual.simple;


import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.SocketException;
import java.util.Arrays;
import java.util.HashMap;

import smartsockets.virtual.VirtualServerSocket;
import smartsockets.virtual.VirtualSocket;
import smartsockets.virtual.VirtualSocketAddress;
import smartsockets.virtual.VirtualSocketFactory;

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
 
        s.setTcpNoDelay(true);
    
        System.out.println("Configured socket: ");         
        System.out.println(" sendbuffer     = " + s.getSendBufferSize());
        System.out.println(" receiverbuffer = " + s.getReceiveBufferSize());
        System.out.println(" no delay       = " + s.getTcpNoDelay());        
    }
    
    public static void client(VirtualSocketAddress target) { 
        
        try { 
            long [] detailedDirect = new long[1+2*target.machine().numberOfAddresses()];
            long [] detailedVirtual = new long[5];

            connectProperties.put("direct.detailed.timing", detailedDirect);
            connectProperties.put("virtual.detailed.timing", detailedVirtual);
            
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
                   
                if (mbit > 1000) { 
                    mbit = mbit / 1024.0;
                    System.out.printf("Test took %d ms. Througput = %4.1f " +
                            "MByte/s (%3.1f GBit/s)\n", time, tp, mbit);
                } else { 
                    System.out.printf("Test took %d ms. Througput = %4.1f " +
                            "MByte/s (%3.1f MBit/s)\n", time, tp, mbit);
                }

                System.out.println("Details direct : " + Arrays.toString(detailedDirect));
                Arrays.fill(detailedDirect, 0);

                System.out.println("Details virtual: " + Arrays.toString(detailedVirtual));
                Arrays.fill(detailedVirtual, 0);

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
                
        connectProperties = new HashMap();

        VirtualSocketAddress target = null;

        for (int i=0;i<args.length;i++) { 
            if (args[i].equals("-target")) {                                 
                target = new VirtualSocketAddress(args[++i]);
            } else if (args[i].equals("-size")) {                                 
                size = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-count")) {                                 
                count = Integer.parseInt(args[++i]);    
            } else if (args[i].equals("-repeat")) {                                 
                repeat = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-buffers")) {                                 
                int size = Integer.parseInt(args[++i]);                            
                connectProperties.put("sendbuffer", size);
                connectProperties.put("receivebuffer", size);
            } else { 
                System.err.println("Unknown option: " + args[i]);                
            }
        }

        sf = VirtualSocketFactory.createSocketFactory(connectProperties, true);
        
        if (target == null) { 
            server();
        } else { 
            client(target);
        }
    }
}
