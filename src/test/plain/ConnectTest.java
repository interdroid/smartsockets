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

        try {

            if (args.length > 0) {

                int start = 0;
                int repeat = REPEAT;
                int count = COUNT;
                
                // This parsing is lame!
                if (args[0].equals("-repeat")) { 
                    repeat = Integer.parseInt(args[1]);
                    start += 2;
                }
                
                if (args[start].equals("-count")) { 
                    count = Integer.parseInt(args[start+1]);
                    start += 2;
                }
                
                for (int i=start; i < args.length / 2; i++) {
                    
                    System.out.println("Creating connection to " + args[i] 
                               + ":" + args[i+1]);
                    
                    InetSocketAddress a = new InetSocketAddress(args[i], Integer.parseInt(args[i+1]));

                    for (int r = 0; r < repeat; r++) {
                    
                        long time = System.currentTimeMillis();
                    
                        for (int c = 0; c < count; c++) {
                            Socket s = new Socket(a.getAddress(), a.getPort());
                       
                            OutputStream out = s.getOutputStream();

                            out.write(42);
                            out.flush();
                            
                            InputStream in = s.getInputStream();
                            in.read();
                            
                            in.close();
                            out.close();
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

                    InputStream in = s.getInputStream();
                    in.read();
                    
                    OutputStream out = s.getOutputStream();

                    out.write(42);
                    out.flush();
                    
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
