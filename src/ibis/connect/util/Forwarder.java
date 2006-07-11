package ibis.connect.util;

import java.io.InputStream;
import java.io.OutputStream;

public class Forwarder extends Thread {

    private static final int DEFAULT_BUFFER_SIZE = 16*1024;

    public final byte [] buffer;
    public final InputStream in;
    public final OutputStream out;
    
    private int bytes;    
    
    private final ForwarderDoneCallback cb;
    private final Object id;    
    private final String label;
    
    private boolean done = false;
    
    public Forwarder(InputStream in, OutputStream out) { 
        this(in, out, null, null, "unknown", DEFAULT_BUFFER_SIZE);
    }
    
    public Forwarder(InputStream in, OutputStream out, ForwarderDoneCallback cb, Object id, 
            String label) { 
        
        this(in, out, cb, id, label, DEFAULT_BUFFER_SIZE);
    }

    public Forwarder(InputStream in, OutputStream out, ForwarderDoneCallback cb, Object id, 
            String label, int bufferSize) {
        
        this.in = in;
        this.out = out;
        this.id = id;
        this.cb = cb;
        this.label = label;
        this.buffer = new byte[bufferSize];        
    }
    
    public synchronized boolean isDone() {
        return done;
    }
    
    private void close() {
        try { 
            in.close();
        } catch (Exception e) {
            // ignore
        }
        
        try { 
            out.close();
        } catch (Exception e) {
            // ignore
        }
    }
    
    public void run() {
        
        System.out.println("Forwarder " + label + " running!");
        
        while (!done) {            
            try {           
                int n = in.read(buffer);
                
                System.out.println("Forwarder " + label + " read " + n + " bytes");                                    
                
                if (n == -1) {
                    close();
                    
                    synchronized (this) {
                        done = true;
                    } 
                } else if (n > 0) {
                    out.write(buffer, 0, n);
                    out.flush();
                    bytes += n;
                }
                
            } catch (Exception e) {
                System.err.println("Forwarder " + label + " got exception!");
                e.printStackTrace(System.err);
                synchronized (this) {
                    done = true;
                }
            }
        } 
                       
        if (cb != null) {            
            cb.done(id);
        }
    }    
}
