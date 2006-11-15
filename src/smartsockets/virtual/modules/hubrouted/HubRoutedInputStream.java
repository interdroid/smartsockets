package smartsockets.virtual.modules.hubrouted;

import java.io.IOException;
import java.io.InputStream;

public class HubRoutedInputStream extends InputStream {

    private final HubRoutedVirtualSocket parent; 
    
    private byte [] buffer;    
    private int used = 0;
    private boolean closed = false;
        
    HubRoutedInputStream(HubRoutedVirtualSocket parent) { 
        this.parent = parent;
    }
    
    public int read() throws IOException {
        
        byte [] tmp = new byte[1];
        
        int result = read(tmp);
        
        if (result == 1) { 
            return tmp[0];
        } else { 
            return result;
        }
    }

    public int read(byte[] b) throws IOException { 
        return read(b, 0, b.length);
    }
    
    public int read(byte[] b, int off, int len) throws IOException { 

        if (closed) { 
            throw new IOException("Stream closed!");
        }
        
        if (buffer == null || used == buffer.length) {
            buffer = parent.getBuffer(buffer);
            used = 0;
        }
        
        int avail = buffer.length - used;
            
        if (len < avail) { 
            System.arraycopy(buffer, used, b, off, len);
            used += len;
            return len;                
        } else { 
            System.arraycopy(buffer, used, b, off, avail);
            used = buffer.length;
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
    
    public boolean closed() { 
        return closed;
    }
    
}
