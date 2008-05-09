package test.ssh;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

//import ch.ethz.ssh2.Connection;
//import ch.ethz.ssh2.LocalStreamForwarder;

import com.trilead.ssh2.Connection;
import com.trilead.ssh2.LocalStreamForwarder;

public class Simple {

    private static String filename = "/home/jason/.ssh/id_rsa";
    // or "~/.ssh/id_dsa"
    
    /**
     * @param args
     */
    public static void main(String[] args) {
     
        if (args.length == 3) {
            String user = args[0];
            String host = args[1];
        
            int port = Integer.parseInt(args[2]);
            
            client(user, host, port);
        } else { 
            server();
        }
    } 
      
    private static void server() { 
        
        try {
            
            ServerSocket ss = new ServerSocket(0);
            
            System.out.println("Server listening on port: " + ss.getLocalPort());
            
            Socket s = ss.accept();
            
            DataInputStream in = new DataInputStream(s.getInputStream());            
            DataOutputStream out = new DataOutputStream(s.getOutputStream());
            
            String reply = in.readUTF();

            System.out.println("Client says: " + reply);

            out.writeUTF("Hello");
            out.flush();


            out.close();
            in.close();
            s.close();
            
        } catch (Exception e) {
            System.err.println("Eek: " + e);
            e.printStackTrace(System.err);
        }
    }
    
    private static void client(String user, String host, int port) { 
    
        try { 
            Connection conn = new Connection(host);

            conn.connect();

            // TODO: quick hack.... fix this!!
            File keyfile = new File(filename); 
            String keyfilePass = "joespass"; // will be ignored if not needed

            boolean isAuthenticated = conn.authenticateWithPublicKey(user, 
                    keyfile, keyfilePass);

            if (isAuthenticated == false)
                throw new IOException("Authentication failed.");

            LocalStreamForwarder lsf = 
                conn.createLocalStreamForwarder(host, port);

            DataInputStream in = new DataInputStream(lsf.getInputStream());
            DataOutputStream out = new DataOutputStream(lsf.getOutputStream());

            out.writeUTF("Hello");
            out.flush();

            String reply = in.readUTF();

            System.out.println("Server says: " + reply);

            out.close();
            in.close();
            lsf.close();
        } catch (Exception e) {
            System.err.println("Eek: " + e);
            e.printStackTrace(System.err);
        }
    } 
}
