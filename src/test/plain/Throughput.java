package test.plain;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

public class Throughput {

    private final static int REPEAT = 10;
    private final static int COUNT = 100;
    private final static int SIZE = 1024*1024;
    private final static int BUFFER_SIZE = 1024*1024;
    
    private static int inbuf = BUFFER_SIZE;
    private static int outbuf = BUFFER_SIZE;
    
    private static void configure(Socket s) throws SocketException { 
        
        s.setSendBufferSize(inbuf);
        s.setReceiveBufferSize(outbuf);
        s.setTcpNoDelay(true);
    
        System.out.println("Configured socket: ");         
        System.out.println(" sendbuffer     = " + s.getSendBufferSize());
        System.out.println(" receiverbuffer = " + s.getReceiveBufferSize());
        System.out.println(" no delay       = " + s.getTcpNoDelay());        
    }
    
    public static void main(String[] args) {

        int targets = args.length;
        int repeat = REPEAT;        
        int count = COUNT;
        int size = SIZE;
        
        for (int i=0;i<args.length;i++) { 
            if (args[i].equals("-repeat")) { 
                repeat = Integer.parseInt(args[i+1]);
                args[i+1] = null;
                args[i] = null;
                targets -= 2;
                i++;
                
            } else if (args[i].equals("-count")) { 
                count = Integer.parseInt(args[i+1]);
                args[i+1] = null;
                args[i] = null;
                targets -= 2;
                i++;
        
            } else if (args[i].equals("-size")) { 
                size = Integer.parseInt(args[i+1]);
                args[i+1] = null;
                args[i] = null;
                targets -= 2;
                i++;
        
            } else if (args[i].equals("-buffers")) { 
                inbuf = outbuf = Integer.parseInt(args[i+1]);
                args[i+1] = null;
                args[i] = null;
                targets -= 2;
                i++;
            
            } else if (args[i].equals("-inbuf")) { 
                inbuf = Integer.parseInt(args[i+1]);
                args[i+1] = null;
                args[i] = null;
                targets -= 2;
                i++;
                
            } else if (args[i].equals("-outbuf")) { 
                outbuf = Integer.parseInt(args[i+1]);
                args[i+1] = null;
                args[i] = null;
                targets -= 2;
                i++;
            } 
        }
        
        InetSocketAddress [] targetAds = new InetSocketAddress[targets];
        int index = 0;
        
        for (int i=0;i<args.length-1;i++) { 
            if (args[i] != null && args[i+1] != null) { 
                targetAds[index++] = new InetSocketAddress(args[i], Integer.parseInt(args[i+1])); 
            }
        } 
        
        
        try {
            if (index > 0) {

                byte [] data = new byte[size];
                
                for (InetSocketAddress a : targetAds) {
                    
                    if (a == null) { 
                        continue;
                    }
                    
                    System.out.println("Creating connection to " + a);
                    
                    Socket s = new Socket();
                    s.setReuseAddress(true);
                    s.connect(a);
                    
                    configure(s);
                    
                    DataOutputStream out = new DataOutputStream(s.getOutputStream());
                    DataInputStream in = new DataInputStream(s.getInputStream());
                    
                    out.writeInt(count);
                    out.writeInt(repeat);
                    out.writeInt(size);
                    out.flush();
                    
                    for (int r = 0; r < repeat; r++) {
                    
                        long time = System.currentTimeMillis();
                    
                        for (int c = 0; c < count; c++) {
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
                    
                    out.close();
                    in.close();
                    s.close();
                    
                }
            } else {

                System.out.println("Creating server socket");

                ServerSocket ss = new ServerSocket(0, 100);

                System.out.println("Created server on " + ss.toString());

                while (true) {
                    Socket s = ss.accept();
                    
                    configure(s);
                    
                    DataOutputStream out = new DataOutputStream(s.getOutputStream());
                    DataInputStream in = new DataInputStream(s.getInputStream());
                    
                    count = in.readInt();
                    repeat = in.readInt();
                    size = in.readInt();    
                    
                    byte [] data = new byte[size];
                    
                    for (int r = 0; r < repeat; r++) {
                        for (int c = 0; c < count; c++) {
                            in.readFully(data);
                        }
                        
                        out.write(42);
                        out.flush();
                    }
                                        
                    in.close();
                    out.close();
                    s.close();
                }
            }

        } catch (Exception e) {
            System.out.println("EEK!");
            e.printStackTrace(System.err);
        }
    }
}
;