package test.virtual.simple;

import ibis.connect.virtual.VirtualServerSocket;
import ibis.connect.virtual.VirtualSocket;
import ibis.connect.virtual.VirtualSocketAddress;
import ibis.connect.virtual.VirtualSocketFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

 
public class ConnectTest {
    
    public static void main(String [] args) throws IOException { 
        
        VirtualSocketFactory sf = VirtualSocketFactory.getSocketFactory();
        
        if (args.length > 0) {             
            for (int i=0;i<args.length;i++) { 
                VirtualSocketAddress target = new VirtualSocketAddress(args[i]);
                VirtualSocket s = sf.createClientSocket(target, 0, null);
                
                System.out.println("Created connection to " + target);

                DataInputStream in = new DataInputStream(s.getInputStream());
                DataOutputStream out = new DataOutputStream(s.getOutputStream());

                System.out.println("Server says: " + in.readUTF());
                                
                out.writeUTF("Hello server!");
                out.flush();
                           
                in.close();
                out.close();                
                s.close();
            }
        } else {                         
            System.out.println("Creating server socket");
            
            VirtualServerSocket ss = sf.createServerSocket(0, 0, null);
            
            System.out.println("Created server on " + ss.getLocalSocketAddress());
                        
            while (true) {
                System.out.println("Server waiting for connections"); 
                
                VirtualSocket s = ss.accept();
                                
                System.out.println("Incoming connection from " 
                        + s.getRemoteSocketAddress());
                
                DataInputStream in = new DataInputStream(s.getInputStream());
                DataOutputStream out = new DataOutputStream(s.getOutputStream());
                
                out.writeUTF("Hello client!");
                out.flush();
                
                System.out.println("Client says: " + in.readUTF());
                                
                in.close();
                out.close();
                s.close();
            }
        }
    }
}
