package smartsockets.virtual.modules.hubrouted;

import java.io.IOException;
import java.io.OutputStream;

public class HubRoutedOutputStream extends OutputStream {

    private final HubRoutedVirtualSocket parent; 
    
    private final byte [] buffer;    
    private int used = 0;
    private boolean closed = false;
        
    HubRoutedOutputStream(HubRoutedVirtualSocket parent, int size) { 
        this.parent = parent;
        buffer = new byte[size];
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
        
            int space = buffer.length-used;
            
            if (len <= space) {
                
                //System.err.println("OutputStream has enough space: " + len 
                  //      + "(" + space + ")");
                
                System.arraycopy(b, off, buffer, used, len);            
                used += len;             
                return;
            } 
         
  ///          System.err.println("OutputStream has not enough space: " + len 
                    //+ "(" + space + ")");
                        
            System.arraycopy(b, off, buffer, used, space);        
            
            used = buffer.length;
            
            len -= space;
            off += space;
                    
            flush();
        }
    }
    
    public void flush() throws IOException {
        
//        System.err.println("OutputStream flushing: " + used + " bytes"); 

        if (closed) { 
            return;
        }
        
        parent.flush(buffer, 0, used);
        used = 0;        
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
