package test.plain;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Inet4Address;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;

import smartsockets.direct.DirectServerSocket;
import smartsockets.direct.DirectSocket;
import smartsockets.direct.DirectSocketFactory;
import smartsockets.direct.SocketAddressSet;

 
public class ConnectTest {
    
    public static void main(String [] args) { 
        
        try { 
            
        if (args.length > 0) {             
            for (int i=0;i<args.length;i++) {
                
                long time = System.currentTimeMillis();
                
                Socket s = new Socket(args[0], Integer.parseInt(args[1]));
                
                time = System.currentTimeMillis() - time;
                
                System.out.println("Created connection to " + s +
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
        } else {                         
      
            System.out.println("Creating server socket");
            
            ServerSocket ss = new ServerSocket(0, 100);
            
            System.out.println("Created server on " + ss.toString());
                        
            while (true) {
                Socket s = ss.accept();
                                
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
