package test.virtual.throughput.mt;

import ibis.smartsockets.virtual.InitializationException;
import ibis.smartsockets.virtual.VirtualServerSocket;
import ibis.smartsockets.virtual.VirtualSocket;
import ibis.smartsockets.virtual.VirtualSocketAddress;
import ibis.smartsockets.virtual.VirtualSocketFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.SocketException;
import java.util.HashMap;

public class MTThroughput {
    
    private static int OPCODE_META = 42;    
    private static int OPCODE_DATA = 24;    
       
    private static int DEFAULT_STREAMS = 2;    
    private static int DEFAULT_REPEAT = 10;
    private static int DEFAULT_COUNT = 100;
    
    private static int TIMEOUT = 15000;
    private static int DEFAULT_SIZE = 1024*1024;
    
    private static int count = DEFAULT_COUNT;    
    private static int repeat = DEFAULT_REPEAT;
    private static int size = DEFAULT_SIZE;
    private static int streams = DEFAULT_STREAMS;
   
    private static VirtualSocketFactory sf;    
    private static HashMap<String, Object> connectProperties;
    
    private static void configure(VirtualSocket s) throws SocketException { 
 
        s.setTcpNoDelay(true);
    
        System.out.println("Configured socket: ");         
        System.out.println(" sendbuffer     = " + s.getSendBufferSize());
        System.out.println(" receiverbuffer = " + s.getReceiveBufferSize());
        System.out.println(" no delay       = " + s.getTcpNoDelay());        
    }
    
    private static void createStreamOut(VirtualSocketAddress target, DataSource d, int id, int size) { 

        try { 
            VirtualSocket s = sf.createClientSocket(target, TIMEOUT, 
                    connectProperties);

            System.out.println("Created DATA connection to " + target);

            configure(s);

            DataInputStream in = new DataInputStream(s.getInputStream());
            DataOutputStream out = new DataOutputStream(s.getOutputStream());

            out.writeInt(OPCODE_DATA);            
            out.writeInt(id);
            out.flush();                

            new Sender(d, s, out, in, size).start();
            
        } catch (Exception e) {
            System.out.println("Failed to create connection to " + target); 
        }
    }
    
    private static void printPerformance(long time, long size) { 
      
        double tp = (1000.0 * size) / (1024.0*1024.0*time);  
        double mbit = (8000.0 * size) / (1024.0*1024.0*time);  
           
        if (mbit > 1000) { 
            mbit = mbit / 1024.0;
            System.out.printf("Test took %d ms. Througput = %4.1f " +
                    "MByte/s (%3.1f GBit/s)\n", time, tp, mbit);
        } else { 
            System.out.printf("Test took %d ms. Througput = %4.1f " +
                    "MByte/s (%3.1f MBit/s)\n", time, tp, mbit);
        }
    }
    
    public static void client(VirtualSocketAddress target) { 
        
        try { 
            VirtualSocket s = sf.createClientSocket(target, TIMEOUT, 
                    connectProperties);

            System.out.println("Created META connection to " + target);

            configure(s);

            DataInputStream in = new DataInputStream(s.getInputStream());
            DataOutputStream out = new DataOutputStream(s.getOutputStream());

            // TODO: draw random ID here!
            int id = 42;
            
            out.writeInt(OPCODE_META);            
            out.writeInt(size);
            out.writeInt(count);
            out.writeInt(repeat);
            out.writeInt(streams);            
            out.writeInt(id);
            out.flush();                

            DataSource d = new DataSource();
            
            for (int i=0;i<streams;i++) { 
                createStreamOut(target, d, id, size);
            }
 
            System.out.println("Starting test");

            for (int r=0;r<repeat;r++) {
                
                long start = System.currentTimeMillis();
                
                d.set(count);
                in.readInt();
                
                long end = System.currentTimeMillis();
                
                printPerformance(end-start, count*size);
                
                // System.out.println("Send " + count + " (" + tmp + ")");
                
                // TODO: print some stats here!
            } 
            
            d.done();
           
            VirtualSocketFactory.close(s, out, in);

        } catch (Exception e) {
            System.out.println("Failed to create connection to " + target); 
        }
    }


    private static void createStreamIn(VirtualServerSocket ss, DataSink d, 
            int id, int size) { 

        try { 
            VirtualSocket s = ss.accept();

            System.out.println("Incoming connection from " 
                    + s.getRemoteSocketAddress());

            configure(s);

            DataInputStream in = new DataInputStream(s.getInputStream());
            DataOutputStream out = new DataOutputStream(s.getOutputStream());

            int opcode = in.readInt();

            if (opcode != OPCODE_DATA) { 
                System.err.println("EEK: sender out of sync (2)!");
                System.exit(1);
            }
            
            int tmp = in.readInt();
            
            if (tmp != id) { 
                System.err.println("EEK: sender out of sync (3)!");
                System.exit(1);
            }

            new Receiver(d, s, out, in, size).start();
                        
        } catch (Exception e) { 
            System.err.println("EEK: got exception while accepting! " + e);
            System.exit(1);
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
                
                int opcode = in.readInt();
                
                if (opcode != OPCODE_META) { 
                    System.err.println("EEK: sender out of sync!");
                    System.exit(1);
                }
                
                size = in.readInt();
                count = in.readInt();                              
                repeat = in.readInt();                              
                streams = in.readInt();                              
            
                int id = in.readInt();
                
                DataSink d = new DataSink();
                
                for (int i=0;i<streams;i++) { 
                    createStreamIn(ss, d, id, size);
                }
     
                for (int r=0;r<repeat;r++) {
                    
                    d.waitForCount(streams);
                    out.writeInt(streams);
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
                
        connectProperties = new HashMap<String, Object>();

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
            } else if (args[i].equals("-streams")) {                                 
                streams = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-buffers")) {                                 
                int size = Integer.parseInt(args[++i]);                            
                connectProperties.put("sendbuffer", size);
                connectProperties.put("receivebuffer", size);
            } else { 
                System.err.println("Unknown option: " + args[i]);                
            }
        }

        try {
            sf = VirtualSocketFactory.createSocketFactory(connectProperties, true);
        } catch (InitializationException e) {
            System.out.println("Failed to create socketfactory!");
            e.printStackTrace();
            System.exit(1);
        }
        
        if (target == null) { 
            server();
        } else { 
            client(target);
        }
    }
}
