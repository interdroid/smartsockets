package test.plain;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class Latency {

    private final static int PORT = 8899;    
    private final static int REPEAT = 10;
    private final static int COUNT = 1000;

    public static void main(String[] args) {

        int targets = args.length;
        int repeat = REPEAT;        
        int count = COUNT;
        int port = PORT; 
        
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
        
            } else if (args[i].equals("-port")) { 
                port = Integer.parseInt(args[i+1]);
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

                for (InetSocketAddress a : targetAds) {
                    
                    if (a == null) { 
                        continue;
                    }
                    
                    System.out.println("Creating connection to " + a);
                    
                    Socket s = new Socket();
                    s.setReuseAddress(true);
                    s.connect(a);
                    
                    s.setTcpNoDelay(true);
                    
                    DataOutputStream out = new DataOutputStream(s.getOutputStream());
                    DataInputStream in = new DataInputStream(s.getInputStream());
                    
                    out.writeInt(count);
                    out.writeInt(repeat);
                    out.flush();
                    
                    for (int r = 0; r < repeat; r++) {
                    
                        long time = System.currentTimeMillis();
                    
                        for (int c = 0; c < count; c++) {
                            out.write(42);
                            out.flush();
                            in.read();
                        }
                     
                        time = System.currentTimeMillis() - time;

                        System.out.println(count + " rtts " + time 
                                + " ms. -> " + (((double) time) / count) 
                                + "ms/rt");
                       
                    }
                    
                    out.close();
                    in.close();
                    s.close();
                    
                }
            } else {

                System.out.println("Creating server socket");

                ServerSocket ss = new ServerSocket(port, 100);

                System.out.println("Created server on " + ss.toString());

                while (true) {
                    Socket s = ss.accept();
                    
                    s.setTcpNoDelay(true);
                    
                    DataOutputStream out = new DataOutputStream(s.getOutputStream());
                    DataInputStream in = new DataInputStream(s.getInputStream());
                    
                    count = in.readInt();
                    repeat = in.readInt();
                                       
                    for (int r = 0; r < repeat; r++) {
                        for (int c = 0; c < count; c++) {
                            in.read();
                            out.write(42);
                            out.flush();
                        }
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