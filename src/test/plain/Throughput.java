package test.plain;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

public class Throughput {

    private final static int PORT = 8899;    
    
    private final static int REPEAT = 10;
    private final static int COUNT = 100;
    private final static int SIZE = 1024*1024;
    
    private static int inbufPre = -1;
    private static int outbufPre = -1;
    
    private static int inbuf = -1;
    private static int outbuf = -1;
        
    private static void configurePre(Socket s) throws SocketException {
        if (inbufPre > 0)
            s.setSendBufferSize(inbufPre);
        
        if (outbufPre > 0)
            s.setReceiveBufferSize(outbufPre);
        
        s.setTcpNoDelay(true);
    } 
    
    private static void configure(Socket s) throws SocketException {     
        
        if (inbuf > 0)        
            s.setSendBufferSize(inbuf);
        
        if (outbuf > 0)        
            s.setReceiveBufferSize(outbuf);
        
        s.setTcpNoDelay(true);
    } 
        
    private static void print(Socket s) throws SocketException { 
        System.out.println("Configured socket: ");         
        System.out.println(" sendbuffer    = " + s.getSendBufferSize());
        System.out.println(" receivebuffer = " + s.getReceiveBufferSize());
        System.out.println(" no delay      = " + s.getTcpNoDelay());        
    }
    
    private static void configurePre(ServerSocket s) throws SocketException {
        
        if (outbufPre > 0)        
            s.setReceiveBufferSize(outbufPre);
    }
    
    
    private static void send(Socket s, DataOutputStream out, DataInputStream in,
            int repeat, int count, byte [] data, int size) throws IOException { 
        
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
    }
    
    private static void receive(Socket s, DataOutputStream out, 
            DataInputStream in, int repeat, int count, byte [] data) throws IOException { 
        
        for (int r = 0; r < repeat; r++) {
            for (int c = 0; c < count; c++) {
                in.readFully(data);
            }
            
            out.write(42);
            out.flush();
        }
        
    }
    
    
    public static void main(String[] args) {

        int targets = args.length;
        int repeat = REPEAT;        
        int count = COUNT;
        int size = SIZE;
        int port = PORT;
        
        boolean swap = false;
        
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
        
            } else if (args[i].equals("-port")) { 
                port = Integer.parseInt(args[i+1]);
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
                
            } else if (args[i].equals("-bufferspre")) { 
                inbufPre = outbufPre = Integer.parseInt(args[i+1]);
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
            
            } else if (args[i].equals("-swap")) { 
                swap = true;
                args[i] = null;
                targets -= 1;
            
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
                    configurePre(s);
                    print(s);

                    s.connect(a);                    
                   
                    configure(s);
                    print(s);

                    DataOutputStream out = new DataOutputStream(s.getOutputStream());
                    DataInputStream in = new DataInputStream(s.getInputStream());
                    
                    out.writeInt(count);
                    out.writeInt(repeat);
                    out.writeInt(size);
                    out.flush();

                    if (swap) { 
                        receive(s, out, in, repeat, count, data);
                    } else { 
                        send(s, out, in, repeat, count, data, size);
                    } 
                    
                    out.close();
                    in.close();
                    s.close();                    
                }
            } else {

                System.out.println("Creating server socket");

                ServerSocket ss = new ServerSocket(port, 100);
                configurePre(ss);                
                
                System.out.println("Created server on " + ss.toString());

                while (true) {
                    Socket s = ss.accept();
                    
                    configure(s);
                    print(s);
                    
                    DataOutputStream out = new DataOutputStream(s.getOutputStream());
                    DataInputStream in = new DataInputStream(s.getInputStream());
                    
                    count = in.readInt();
                    repeat = in.readInt();
                    size = in.readInt();    
                    
                    byte [] data = new byte[size];
                    
                    if (swap) {
                        send(s, out, in, repeat, count, data, size);
                    } else {
                        receive(s, out, in, repeat, count, data);                        
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