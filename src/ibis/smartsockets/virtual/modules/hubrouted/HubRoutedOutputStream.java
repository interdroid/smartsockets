package ibis.smartsockets.virtual.modules.hubrouted;

import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketTimeoutException;

public class HubRoutedOutputStream extends OutputStream {

    private final HubRoutedVirtualSocket parent; 
    
    private final byte [] buffer;
    private final int size;
        
    private int remoteBufferFree;
    
    private int used = 0;
    private boolean closed = false;
        
    HubRoutedOutputStream(HubRoutedVirtualSocket parent, int fragmentation, 
            int bufferSize) {        
        
        this.parent = parent;
        
        this.size = fragmentation;
        buffer = new byte[fragmentation];
   
        remoteBufferFree = bufferSize;
    }
        
    public void write(byte[] b) throws IOException {        
        write(b, 0, b.length);
    }
    
    public void write(int arg0) throws IOException {
        // TODO: very inefficient;
        write(new byte [] { (byte) arg0 });
    }
    
    public void write(byte[] b, int off, int len) throws IOException {
       
        if (closed) { 
            throw new IOException("Stream closed!");
        }
        
        while (len > 0) { 
        
            int space = size-used;
        
            // Data is smaller than space
            if (len <= space) {
                System.arraycopy(b, off, buffer, used, len);            
                used += len;             
                return;
            } 
                
            // Data is larger than space, and there may be some data in buffer
            System.arraycopy(b, off, buffer, used, space);        
            
            used = buffer.length;
                
            len -= space;
            off += space;
                
            flush();
        }
    }
    
    private synchronized void waitForBufferSpace() throws IOException {
        
        long timeleft = parent.getSoTimeout();
        long deadline = 0;
        
        if (timeleft > 0) { 
            deadline = System.currentTimeMillis() + timeleft;
        }
        
        while (remoteBufferFree-used < 0) {
            try { 
                wait(timeleft);
            } catch (InterruptedException e) {
                // ignore
            }
            
            if (remoteBufferFree-used >= 0) { 
                return;
            } 
            
            if (deadline > 0) { 
                // Still no room, and we are on a tight schedule!
                timeleft = deadline - System.currentTimeMillis();
                
                if (timeleft <= 0) { 
                    throw new SocketTimeoutException("Timeout while waiting " +
                            "for buffer space");
                }
            }
        }        
    }
    
    protected synchronized void messageACK(int data) { 
        remoteBufferFree += data;
        notifyAll();
    }
    
    public void flush() throws IOException {
        
        if (closed) { 
            return;
        }
        
        if (used > 0) { 
           
            // Will throw an exception on timeout!
            waitForBufferSpace();
            
            parent.flush(buffer, 0, used);
            remoteBufferFree -= used;
            used = 0;
        }
    }
    
    public void close() throws IOException {
        
        if (closed) { 
            return;
        }
        
        flush();
        closed = true;
    }
    
    public boolean closed() { 
        return closed;
    }
    
}
