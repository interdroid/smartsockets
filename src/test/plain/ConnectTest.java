package test.plain;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class ConnectTest {

    private final static int REPEAT = 10;
    private final static int COUNT = 1000;

    public static void main(String[] args) {

        int targets = args.length;
        int repeat = REPEAT;        
        int count = COUNT;
        
        boolean pingpong = false;
        
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
        
            } else if (args[i].equals("-pingpong")) {
                pingpong = true;
                args[i] = null;
                targets--;
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
                    
                    for (int r = 0; r < repeat; r++) {
                    
                        long time = System.currentTimeMillis();
                    
                        for (int c = 0; c < count; c++) {
                            Socket s = new Socket();
                            s.setReuseAddress(true);
                            s.connect(a);
                            
                            if (pingpong) { 
                                s.setTcpNoDelay(true);
                                
                                OutputStream out = s.getOutputStream();

                                out.write(42);
                                out.flush();
                            
                                InputStream in = s.getInputStream();
                                in.read();
                            
                                in.close();
                                out.close();
                            }
                            
                            s.close();
                        }
                     
                        time = System.currentTimeMillis() - time;

                        System.out.println(count + " connections in " + time 
                                + " ms. -> " + (((double) time) / count) 
                                + "ms/conn");
                       
                    }
                }
            } else {

                System.out.println("Creating server socket");

                ServerSocket ss = new ServerSocket(0, 100);

                System.out.println("Created server on " + ss.toString());

                while (true) {
                    Socket s = ss.accept();
                    
                    if (pingpong) { 
                        s.setTcpNoDelay(true);
                        
                        InputStream in = s.getInputStream();
                        in.read();
                    
                        OutputStream out = s.getOutputStream();

                        out.write(42);
                        out.flush();
                    
                        in.close();
                        out.close();
                    }
                     
                    s.close();
                }
            }

        } catch (Exception e) {
            System.out.println("EEK!");
            e.printStackTrace(System.err);
        }
    }
}
