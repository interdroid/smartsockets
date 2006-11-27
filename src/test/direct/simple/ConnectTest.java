package test.direct.simple;


import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Random;

import smartsockets.direct.DirectServerSocket;
import smartsockets.direct.DirectSocket;
import smartsockets.direct.DirectSocketFactory;
import smartsockets.direct.SocketAddressSet;

 
public class ConnectTest {
    
    private static final int REPEAT = 10;
    
    public static void main(String [] args) { 
        
        try { 
            
        DirectSocketFactory sf = DirectSocketFactory.getSocketFactory();
        
        Random rand = new Random();
        
        int repeat = REPEAT;
        boolean sleep = false;
        
        if (args.length > 0) {
            
            int targetCount = args.length;
            
            for (int i=0;i<args.length;i++) { 
                if (args[i].equals("-repeat")) { 
                    repeat = Integer.parseInt(args[i+1]);
                    args[i+1] = null;
                    args[i] = null;
                    targetCount =- 2;
                    i++;
                } else if (args[i].equals("-sleep")) { 
                    sleep = true;
                    args[i] = null;
                    targetCount--;
                } 
            }
            
            SocketAddressSet [] targets = new SocketAddressSet[targetCount];
            int index = 0;
            
            for (int i=0;i<args.length;i++) { 
                if (args[i] != null) { 
                    targets[index++] = new SocketAddressSet(args[i]); 
                }
            } 
            
            for (int r=0;r<repeat;r++) {
                for (SocketAddressSet t : targets) { 
                    
                    if (sleep) { 
                        try { 
                            Thread.sleep(1000 + rand.nextInt(15000));
                        } catch (Exception e) {
                            // TODO: handle exception
                        }
                    }
                    
                    long time = System.currentTimeMillis();

                    DirectSocket s = sf.createSocket(t, 0, 0, null);

                    time = System.currentTimeMillis() - time;

                    System.out.println("Created connection to " + t + 
                            " on local address " + s.getLocalSocketAddress() 
                            + " remote address " + s.getRemoteSocketAddress() 
                            + " in " + time + " ms.");

                    DataInputStream in = new DataInputStream(s.getInputStream());
                    DataOutputStream out = new DataOutputStream(s.getOutputStream());

                    out.write(42);
                    out.flush();

                    System.out.println("Server says: " + in.read());

                    in.close();
                    out.close();                               
                    s.close();
                }
            }
        } else {                         
            System.out.println("Creating server socket");
            
            DirectServerSocket ss = sf.createServerSocket(0, 0, null);
            
            System.out.println("Created server on " + ss.getAddressSet());
                        
            while (true) {
                DirectSocket s = ss.accept();
                                
                System.out.println("Incoming connection from " 
                        + s.getRemoteSocketAddress() + " " + s.getPort());
              
                DataInputStream in = new DataInputStream(s.getInputStream());
                DataOutputStream out = new DataOutputStream(s.getOutputStream());
                
                System.out.println("Client says: " + in.read());
               
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
