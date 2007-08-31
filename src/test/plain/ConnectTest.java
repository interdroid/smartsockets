package test.plain;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.ServerSocket;
import java.net.Socket;

public class ConnectTest {

    private final static int REPEAT = 10;
    private final static int COUNT = 1000;

    public static void main(String[] args) {

        int targets = args.length;
        int repeat = REPEAT;        
        int count = COUNT;
        
        int sport = 0;         
        int cport = 0;
        int delay = 0;
        
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
            
            } else if (args[i].equals("-serverport")) {                
                sport = Integer.parseInt(args[i+1]);
                args[i+1] = null;
                args[i] = null;
                targets -= 2;
                i++;
                
            } else if (args[i].equals("-clientport")) {                
                cport = Integer.parseInt(args[i+1]);
                args[i+1] = null;
                args[i] = null;
                targets -= 2;
                i++;
                
            } else if (args[i].equals("-delay")) {                
                delay = Integer.parseInt(args[i+1]);
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

                    int backoff = 100;
                    
                    for (int r = 0; r < repeat; r++) {
                    
                        long time = System.currentTimeMillis();
                    
                        for (int c = 0; c < count; c++) {
                            Socket s = new Socket();
                            
                            if (cport > 0) { 
                                s.setReuseAddress(true);                            
                                s.bind(new InetSocketAddress(cport));
                            }
                        
                            try { 
                                s.connect(a);
                                
                                backoff = 100;
                            } catch (NoRouteToHostException e) {
                                
                                System.err.println("Connect failed: " + e.getMessage());                                

                                if (e.getMessage().trim().equals("Cannot assign requested address")) { 
                                    
                                    System.err.println("Sleep: " + backoff);
                                    
                                    try {                                        
                                        Thread.sleep(backoff);
                                        backoff *= 2;                                        
                                    } catch (Exception x) { 
                                        // ignore;
                                    }
                                }
                            }
                            
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
                            
                            if (delay > 0) { 
                                try { 
                                    Thread.sleep(delay);
                                } catch (Exception x) { 
                                    // ignore;
                                }
                            }                            
                        }
                     
                        time = System.currentTimeMillis() - time;

                        System.out.println(count + " connections in " + time 
                                + " ms. -> " + (((double) time) / count) 
                                + "ms/conn");
                       
                    }
                }
            } else {

                System.out.println("Creating server socket");

                ServerSocket ss = new ServerSocket();
                ss.setReuseAddress(true);
                
                if (sport == 0) { 
                    sport = 50123;
                }
                
                ss.bind(new InetSocketAddress(sport), 100);
                
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

        } catch (Throwable e) {
            System.out.println("EEK!");
            e.printStackTrace(System.err);
        }
    }
}
