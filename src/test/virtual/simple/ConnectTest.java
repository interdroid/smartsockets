package test.virtual.simple;


import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Random;

import smartsockets.direct.SocketAddressSet;
import smartsockets.virtual.VirtualServerSocket;
import smartsockets.virtual.VirtualSocket;
import smartsockets.virtual.VirtualSocketAddress;
import smartsockets.virtual.VirtualSocketFactory;

 
public class ConnectTest {
    
    private static int REPEAT = 1;
    private static int TIMEOUT = 5000;
        
    private static VirtualSocketFactory sf;
    
    private static HashMap connectProperties;
    
    private static Random rand = new Random();
    
    public static void connect(VirtualSocketAddress target) { 
        
        long time = System.currentTimeMillis();

        try { 
            VirtualSocket s = sf.createClientSocket(target, TIMEOUT, 
                    connectProperties);

            time = System.currentTimeMillis() - time;

            System.out.println("Created connection to " + target + " in " + 
                    time + " ms.");

            DataInputStream in = new DataInputStream(s.getInputStream());
            DataOutputStream out = new DataOutputStream(s.getOutputStream());

            System.out.println("Server says: " + in.readUTF());

            out.writeUTF("Hello server!");
            out.flush();

            VirtualSocketFactory.close(s, out, in);
        } catch (Exception e) {
            time = System.currentTimeMillis() - time;

            System.out.println("Failed to create connection to " + target + 
                    " after " + time + " ms.");
            e.printStackTrace();
        }
    }
    
    public static void accept() throws IOException {
        
        System.out.println("Creating server socket");
        
        VirtualServerSocket ss = sf.createServerSocket(0, 0, connectProperties);
        
        System.out.println("Created server on " + ss.getLocalSocketAddress());
                    
        while (true) {
            System.out.println("Server waiting for connections"); 
            
            try { 
                VirtualSocket s = ss.accept();
                            
                System.out.println("Incoming connection from " 
                        + s.getRemoteSocketAddress());
            
                DataInputStream in = new DataInputStream(s.getInputStream());
                DataOutputStream out = new DataOutputStream(s.getOutputStream());
            
                out.writeUTF("Hello client!");
                out.flush();
            
                System.out.println("Client says: " + in.readUTF());

                VirtualSocketFactory.close(s, out, in);
            } catch (Exception e) {
                System.out.println("Server got exception " + e); 
                e.printStackTrace();
            }
        }
    }
    
    public static void main(String [] args) throws IOException { 
        
        sf = VirtualSocketFactory.createSocketFactory();
        
        connectProperties = new HashMap();

        int targets = args.length;
        int repeat = REPEAT;        
        boolean sleep = false;
        
        for (int i=0;i<args.length;i++) { 
            if (args[i].equals("-cache")) {                 
                connectProperties.put("cache.winner", null);
                args[i] = null;
                targets--;
            } else if (args[i].equals("-repeat")) { 
                repeat = Integer.parseInt(args[i+1]);
                args[i+1] = null;
                args[i] = null;
                targets -= 2;
                i++;
            } else if (args[i].equals("-sleep")) { 
                sleep = true;
                args[i] = null;
                targets--;
            } 
        }

        VirtualSocketAddress [] targetAds = new VirtualSocketAddress[targets];
        int index = 0;
        
        for (int i=0;i<args.length;i++) { 
            if (args[i] != null) { 
                targetAds[index++] = new VirtualSocketAddress(args[i]); 
            }
        } 
        
        if (targets > 0) {             
            
            for (int r=0;r<repeat;r++) {        
                for (VirtualSocketAddress a : targetAds) { 
                    
                    if (sleep) { 
                        try { 
                            Thread.sleep(1000 + rand.nextInt(5000));
                        } catch (Exception e) {
                            // TODO: handle exception
                        }
                    }
                    
                    connect(a);
                }
            }
        } else {
            accept();
        }
        
        sf.printStatistics("");
    }
}
