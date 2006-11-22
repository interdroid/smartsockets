package smartsockets.virtual.modules.hubrouted;

import java.io.IOException;
import java.io.InputStream;

public class HubRoutedInputStream extends InputStream {

    private final HubRoutedVirtualSocket parent; 
    
    private byte [] buffer;    
    private int used = 0;
    
    private boolean closePending = false;
    private boolean closed = false;
        
    private final byte [] single = new byte[1];
    
    HubRoutedInputStream(HubRoutedVirtualSocket parent) { 
        this.parent = parent;
    }
    
    public int read() throws IOException {
        
        int result = read(single);
        
        while (result == 0) {
            // TODO: is this right ? 
            result = read(single);
        }
        
        if (result == 1) {
            //System.err.println("InputStream returning single byte: " + single[0]);            
            return (single[0] & 0xff);
        } else {
            //System.err.println("InputStream returning single result: " + result);            
            return result;
        }
    }

    public int read(byte[] b) throws IOException { 
        
 //       System.err.println("InputStream read(byte[])");
        
        return read(b, 0, b.length);
    }
    
    public int read(byte[] b, int off, int len) throws IOException { 

        if (closed) { 
            throw new IOException("Stream closed!");
        }
        
        if (buffer == null || used == buffer.length) {
          
            buffer = parent.getBuffer(buffer);
         
            if (buffer == null) { 
                close();
                return -1;
            }
            
   //         System.err.println("InputStream got byte[" + buffer.length + "]");
            
            used = 0;
        }
        
        int avail = buffer.length - used;
            
        //System.err.println("InputStream has byte[" + buffer.length + "] used = "
                //+ used + " avail = "+ avail); 
        
        if (len <= avail) { 
            //System.err.println("InputStream has enough bytes: " + avail 
                    //+ " (" + len + ")");            
            
            System.arraycopy(buffer, used, b, off, len);
            used += len;
            
            //System.err.println("InputStream returning len: " + len);            
            return len;                
        } else { 
            //System.err.println("InputStream has too few bytes: " + avail  
              //      + " (" + len + ")");            
            
            System.arraycopy(buffer, used, b, off, avail);
            used = buffer.length;
            
            //System.err.println("InputStream returning avail: " + avail);
            return avail;
        }               
    }
    
    public int available() { 
        
        if (buffer == null) { 
            return 0;
        } else { 
            return buffer.length - used;
        }       
    }
    
    public void close() { 
        closed = true;
    }
    
    public void closePending() { 
        closePending = true;
    }
    
    public boolean closed() { 
        return closed;
    }
    
}
