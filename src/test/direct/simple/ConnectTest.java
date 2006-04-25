package test.direct.simple;

import ibis.connect.direct.DirectServerSocket;
import ibis.connect.direct.DirectSocket;
import ibis.connect.direct.DirectSocketFactory;
import ibis.connect.direct.SocketAddressSet;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

 
public class ConnectTest {
    
    public static void main(String [] args) throws IOException { 
        
        DirectSocketFactory sf = DirectSocketFactory.getSocketFactory();
        
        if (args.length > 0) {             
            for (int i=0;i<args.length;i++) { 
                SocketAddressSet target = new SocketAddressSet(args[i]);
                DirectSocket s = sf.createSocket(target, 0, null);
                
                System.out.println("Created connection to " + target);

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
            
            DirectServerSocket ss = sf.createServerSocket(0, 0, null);
            
            System.out.println("Created server on " + ss.getAddressSet());
                        
            while (true) {
                DirectSocket s = ss.accept();
                                
                System.out.println("Incoming connection from " 
                        + s.getRemoteSocketAddress());
                
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
    }
}
