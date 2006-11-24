package test.direct.simple;


import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Random;

import smartsockets.direct.DirectServerSocket;
import smartsockets.direct.DirectSocket;
import smartsockets.direct.DirectSocketFactory;
import smartsockets.direct.SocketAddressSet;

 
public class ConnectTest {
    
    private final static int REPEAT = 1;
    
    public static void main(String [] args) { 
        
        try { 
            
        DirectSocketFactory sf = DirectSocketFactory.getSocketFactory();
        
        Random rand = new Random();
        
        if (args.length > 0) {
            
            for (int r=0;r<REPEAT;r++) {
                for (int i=0;i<args.length;i++) { 
                    SocketAddressSet target = new SocketAddressSet(args[i]);

                    int sleep = rand.nextInt(15000);
                    
                    try { 
                        Thread.sleep(sleep);
                    } catch (Exception e) {
                        // TODO: handle exception
                    }
                    
                    
                    long time = System.currentTimeMillis();

                    DirectSocket s = sf.createSocket(target, 0, 0, null);

                    time = System.currentTimeMillis() - time;

                    System.out.println("Created connection to " + target + 
                            " on local address " + s.getLocalSocketAddress() 
                            + " remote address " + s.getRemoteSocketAddress() 
                            + " in " + time + " ms.");

                    DataInputStream in = new DataInputStream(s.getInputStream());
                    DataOutputStream out = new DataOutputStream(s.getOutputStream());

                    out.writeUTF("Hello server!");
                    out.flush();

                    System.out.println("Server says: " + in.readUTF());

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
                
                System.out.println("Client says: " + in.readUTF());
                out.writeUTF("Hello client!");
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
