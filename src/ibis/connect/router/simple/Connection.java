/**
 * 
 */
package ibis.connect.router.simple;

import ibis.connect.virtual.VirtualSocket;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

class Connection extends Thread implements Protocol {       

    private static int BUFFER_SIZE = 1024; 
        
    private final Router parent;
    
    private final VirtualSocket sc;
    private final DataOutputStream out;
    private final DataInputStream in;
    
    private DataOutputStream targetOut;
    
    private boolean done = false;
       
    private byte [] buffer;
    private long bytesRead;
    
    boolean connected = false;
            
    Connection(VirtualSocket sc, Router parent) throws IOException {
        
        System.err.println("Created new connection to " + sc);
                
        this.sc = sc;
        this.parent = parent;
        
        // No extra buffer here, since we will mostly send byte[]
        out = new DataOutputStream(sc.getOutputStream());
        in = new DataInputStream(sc.getInputStream());
        
        buffer = new byte[BUFFER_SIZE];
    }
       
    private int readOpcode() throws IOException { 
        return in.read();
    }
        
    private boolean connect() throws IOException { 
         
        boolean succes = false;
        
        System.err.println("Connection " + sc + " waiting for opcode");
               
        try { 
            int opcode = readOpcode();        
            long timeout = in.readLong();
            String key = in.readUTF();
        
            System.err.println("Connection " + sc + " got:");
            System.err.println("     opcode : " + opcode);
            System.err.println("     timeout: " + timeout);
            System.err.println("     key    : " + key);
            
            switch (opcode) {
            case ACCEPT:  
                succes = parent.acceptKey(key, this, timeout);
                break;
            
            case CONNECT:
                succes = parent.offerKey(key, this, timeout);
                break;
                
            default:               
                System.err.println("Got unknown opcode in connect: " + opcode);
            }
        } catch (Exception e) {
            System.err.println("Got exception in connect: " + e);
        }

        System.err.println("Connection " + sc + " setup succes: " + succes);
                
        if (succes) {         
            out.write(OK);
        } else { 
            out.write(FAILED);
        } 
        out.flush();
        
        return succes;        
    }
    
    protected synchronized void waitUntilAccepted() {
        
        while (!connected) { 
            try { 
                wait();
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }
    
    protected void createConnection(Connection other) {
        
        // First do my half...        
        targetOut = other.out;
        other.targetOut = out;            
                             
        connected = true;
        
        // ...then the other half
        synchronized (other) {
            other.connected = true;
            other.notifyAll();
        }
    }
       
    private void forwardData() throws IOException {
        
        while (!done) {
            
            int n = in.read(buffer);
                
            if (n == -1) { 
                done = true;
            } else if (n > 0) {
                targetOut.write(buffer, 0, n);
                bytesRead += n;
            } 
        }       
    }
    
    public void run() { 
        
        try {            
            if (!connect()) {
                System.err.println("Failed to setup connection for " + sc);
                return;
            }              
              
            forwardData();
        } catch (Exception e) {
            // TODO: handle exception
            e.printStackTrace();
        } finally { 
            Router.close(sc, out, in);
        }
    }
    
    public String toString() { 
        return "Connection from " + sc; 
    }
    

}