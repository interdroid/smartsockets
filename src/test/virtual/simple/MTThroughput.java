package test.virtual.simple;

import ibis.smartsockets.virtual.InitializationException;
import ibis.smartsockets.virtual.VirtualServerSocket;
import ibis.smartsockets.virtual.VirtualSocket;
import ibis.smartsockets.virtual.VirtualSocketAddress;
import ibis.smartsockets.virtual.VirtualSocketFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.SocketException;
import java.util.Arrays;
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
    
    private static class DataSource { 

        private int count = 0;
        private boolean done = false;
        
        private synchronized void set(int count) { 
            this.count = count; 
            notifyAll();
        } 
        
        private synchronized void done() { 
            done = true;
            notifyAll();
        }
       
        private synchronized void waitUntilCountDone() { 
            while (count > 0) { 
                try { 
                    wait();
                } catch (Exception e) {
                    // ignore
                }
            }
        } 
       
        
        private synchronized boolean waitForStartOrDone() { 
            while (!done && count == 0) { 
                try { 
                    wait();
                } catch (Exception e) {
                    // ignore
                }
            }
            
            return done;
        } 
            
        private synchronized int getBlock() { 
       
            if (count == 0) { 
                return -1;
            }

            count--;
            
            if (count == 0) { 
                notifyAll();
            }
            
            return count;
        }
    }
    
    private static class DataSink { 

        private int count = 0;
        
        public synchronized void waitForCount(int count) { 

            while (this.count < count) { 
                try { 
                    wait();
                } catch (Exception e) {
                    // ignore
                }
            }
            
            this.count -= count;
        } 
            
        public synchronized void done() { 
            count++;
            notifyAll();
        }
    }
    
    private static class Sender extends Thread { 
    
        private final DataSource d;
        private final VirtualSocket s;
        private final DataInputStream in;
        private final DataOutputStream out;
        private final byte [] data;
        
        private Sender(DataSource d, VirtualSocket s, DataOutputStream out, 
                DataInputStream in, int size) { 
            
            this.d = d;
            this.s = s;
            this.out = out;
            this.in = in;
            this.data = new byte[size];
        }
  
        private void sendData() { 
            
            long time = System.currentTimeMillis();           
            
            int count = 0; 
            int block = d.getBlock();

            while (block != -1) { 
                count++;

                try { 
                    out.writeInt(block);
                    out.write(data);
                    out.flush();
                } catch (Exception e) {
                    System.out.println("Failed to write data!" + e); 
                }
                
                block = d.getBlock();
            }

            try { 
                out.writeInt(-1);
                out.flush();
            } catch (Exception e) {
                System.out.println("Failed to write data!" + e); 
            }   
            
            time = System.currentTimeMillis() - time;

            // TODO: do something with stats!
        }
        
        public void run() { 
            
            boolean done = d.waitForStartOrDone();
        
            while (!done) { 
                sendData();    
                done = d.waitForStartOrDone();
            }
            
            try { 
                out.writeInt(-2);
                out.flush();
            } catch (Exception e) {
                System.out.println("Failed to write data!" + e); 
            }   
        
            VirtualSocketFactory.close(s, out, in);
        }
    }
    
    private static class Receiver extends Thread { 

        private final DataSink d;
        private final VirtualSocket s;
        private final DataInputStream in;
        private final DataOutputStream out;

        private final byte [] data;
        
        private Receiver(DataSink d, VirtualSocket s, DataOutputStream out, 
                DataInputStream in, int size) { 
            
            this.d = d;
            this.s = s;
            this.out = out;
            this.in = in;
            data = new byte[size];
        }
        
        private boolean receiveData() { 
            
            int block = -2;
            
            try { 
                do { 
                    block = in.readInt();
                
                    if (block >= 0) { 
                        in.readFully(data);
                    }   
                } while (block >= 0);
            } catch (Exception e) { 
                System.out.println("Failed to read data!" + e); 
            }

            // TODO: do something with stats ?
            d.done();
            
            return (block == -2);
        }
        
        public void run() { 

            boolean stop = false;
            
            while (!stop) { 
                stop = receiveData();
            }
            
            VirtualSocketFactory.close(s, out, in);
        }
    }
    
    
    
    
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
                
                d.set(count);
                in.read();
                
                // TODO: print some stats here!
            } 
           
            VirtualSocketFactory.close(s, out, in);

        } catch (Exception e) {
            System.out.println("Failed to create connection to " + target); 
        }
    }


    private static void createStreamIn(VirtualServerSocket ss, DataSink d, int id, int size) { 

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

            
            
        } catch (Exception e) { 
            
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

      
    private static void read(VirtualSocket s, DataInputStream in, DataOutputStream out) { 

        try {
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
