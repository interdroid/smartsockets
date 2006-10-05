package smartsockets.direct;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
 
public class Bouncer extends Thread {

    static final String DEFAULT_BOUNCER = "130.37.193.15";
    static final int DEFAULT_PORT = 12345;
            
    private final int port;  
    
    private DirectServerSocket server;    
    private DirectSocketFactory factory;
        
    public Bouncer() {
        this(DEFAULT_PORT);
    } 
        
    public Bouncer(int port) { 
        this.port = port;
        this.factory = DirectSocketFactory.getSocketFactory();
    }
    
    private void createServer() { 
    
        while (server == null) {         
            try {
                server = factory.createServerSocket(port, 0, null);
                server.setSoTimeout(1000);
            } catch (IOException e) {
                System.err.println("Failed to create server socket " + e);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e1) {
                    // ignore
                }
            }
        }
        
        System.out.println("Created Bouncer on " + server);                                                                
    } 
    
    private void closeServer() { 
        try { 
            server.close();
        } catch (Throwable t) {
            // ignore
        } finally { 
            server = null;
        }
    }
    
    private void handleSocket(DirectSocket s) { 

        InputStream in = null;
        OutputStream out = null;
        
        try { 
            s.setSoTimeout(1000);            
            
            in = s.getInputStream();
            
            int opcode = in.read();
            
            if (opcode == 127) { 
                out = s.getOutputStream();
                out.write(s.getLocalAddress().getAddress());
                out.flush();
            }
                                 
        } catch (Throwable e) {            
            System.err.println("Failed to handle Socket: " + e);            
        } finally {
            DirectSocketFactory.close(s, out, in);
        }
    } 
        
    private void accept() {
        
        while (server != null) { 

            try {                
                handleSocket(server.accept());                                           
            } catch (SocketTimeoutException ste) {
                // ignore                
            } catch (IOException e) {
                closeServer();
            }                        
        }
    }
    
    public void run() { 
        
        while (true) {
            
            if (server == null) { 
                createServer();
            }
                      
            accept();           
        }        
    }
    
    public static void main(String [] args) {      
        new Bouncer().start();        
    }       
}
