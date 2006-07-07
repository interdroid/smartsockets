package ibis.connect.gossipproxy;

import ibis.connect.virtual.VirtualSocket;

import java.io.InputStream;
import java.io.OutputStream;

class Forwarder extends Thread {

    private static final int DEFAULT_BUFFER_SIZE = 16*1024;

    public final byte [] buffer;
    public final InputStream in;
    public final OutputStream out;
    
    private int bytes;    
    
    private final Callback cb;
    private final Object id;
    
    private boolean done = false;
    
    public Forwarder(InputStream in, OutputStream out) { 
        this(in, out, null, null, DEFAULT_BUFFER_SIZE);
    }
    
    public Forwarder(InputStream in, OutputStream out, Callback cb, Object id) { 
        this(in, out, cb, id, DEFAULT_BUFFER_SIZE);
    }

    public Forwarder(InputStream in, OutputStream out, Callback cb, Object id, 
            int bufferSize) {
        
        this.in = in;
        this.out = out;
        this.id = id;
        this.cb = cb;
        this.buffer = new byte[bufferSize];        
    }
    
    public synchronized boolean isDone() {
        return done;
    }
    
    public void run() { 
        while (!done) {            
            try {               
                int n = in.read(buffer);
            
                if (n == -1) {
                    synchronized (this) {
                        done = true;
                    } 
                } else if (n > 0) {
                    out.write(buffer, 0, n);
                    bytes += n;
                }
            } catch (Exception e) {
                System.err.println("Forwarder got exception!");
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
