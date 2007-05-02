package test.virtual.simple;

import ibis.smartsockets.util.MultiplexStreamFactory;
import ibis.smartsockets.virtual.InitializationException;
import ibis.smartsockets.virtual.VirtualServerSocket;
import ibis.smartsockets.virtual.VirtualSocket;
import ibis.smartsockets.virtual.VirtualSocketAddress;
import ibis.smartsockets.virtual.VirtualSocketFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;



public class MultiplexTest1 {
    
    public static void main(String [] args) throws IOException { 
        
        VirtualSocketFactory sf = null;
        
        try {
            sf = VirtualSocketFactory.createSocketFactory();
        } catch (InitializationException e1) {
            System.out.println("Failed to create socketfactory!");
            e1.printStackTrace();
            System.exit(1);
        }
        
        if (args.length > 0) {             
            for  (int i=0;i<args.length;i++) { 
                VirtualSocketAddress target = new VirtualSocketAddress(args[i]);
                VirtualSocket s = sf.createClientSocket(target, 0, null);
                
                System.out.println("Created connection to " + target);

                MultiplexStreamFactory f = 
                    new MultiplexStreamFactory(s.getInputStream(), 
                            s.getOutputStream());

                DataInputStream in = new DataInputStream(f.getBaseIn());                
                DataOutputStream out = new DataOutputStream(f.getBaseOut());
                                
                out.writeUTF("Hello server!");
                out.flush();                
                
                out.writeUTF("Hello server!");
                out.flush();                
                
                System.out.println("Server says: " + in.readUTF());                
                
                out.close();
                in.close();
                                
                f.close();                
                s.close();
            }
        } else {                         
            System.out.println("Creating server socket");
            
            VirtualServerSocket ss = sf.createServerSocket(0, 0, null);
            
            System.out.println("Created server on " + ss.getLocalSocketAddress());
                        
            while (true) {
                VirtualSocket s = ss.accept();
                                
                System.out.println("Incoming connection from " 
                        + s.getRemoteSocketAddress());
                                
                MultiplexStreamFactory f = 
                    new MultiplexStreamFactory(s.getInputStream(), 
                            s.getOutputStream());
                
                DataInputStream in = new DataInputStream(f.getBaseIn());                
                DataOutputStream out = new DataOutputStream(f.getBaseOut());
                
                System.out.println("Client says: " + in.readUTF());
                System.out.println("Client says: " + in.readUTF());
                
                out.writeUTF("Hello client!");
                
                out.close();
                in.close();
              
                f.close();                
                s.close();
            }
        }
    }
}
