package smartsockets.virtual.modules.hubrouted;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;

public class HubRoutedInputStream extends InputStream {

    private final int MINIMAL_ACK_SIZE;

    private final HubRoutedVirtualSocket parent; 
     
    // Current buffer 
    private final byte [] buffer;
    
    // Postion in the buffer where we start reading.
    private int startRead = 0;
    
    // Postion in the buffer where we start writing. 
    private int startWrite = 0;
    
    // Amount of data in the buffer
    private int available = 0;
    
    // Amount of data which still needs to be acked.
    private int pendingACK = 0;
    
    // To indicate if we are (about to be) closed.
    private boolean closePending = false;
    private boolean closed = false;
    
    HubRoutedInputStream(HubRoutedVirtualSocket parent, int fragmentation, 
            int bufferSize, int ackSize) { 
        
        this.parent = parent;
        this.buffer = new byte[bufferSize];
        this.MINIMAL_ACK_SIZE = ackSize;
    }
    
    public int read() throws IOException {
        
        if (closed) { 
            throw new IOException("Stream closed!");
        }
    
        // Check how much data we can read. This will block if the buffer is 
        // emtpy, and may throw a TimeOutException 
        int avail = waitAvailable();
        
        // If -1 is returned, the socket was closed
        if (avail == -1) { 
            doClose();
            return -1;
        }
  
        int result = buffer[startRead];
        startRead = (startRead + 1) % buffer.length;
        
        decreaseAvailableAndACK(1);
        
        return result;    
    }

    public int read(byte[] b) throws IOException { 
        return read(b, 0, b.length);
    }
    
    public int read(byte[] b, int off, int len) throws IOException { 

        if (closed) { 
            throw new IOException("Stream closed!");
        }
    
        // Check how much data we can read. This will block if the buffer is 
        // emtpy, and may throw a TimeOutException 
        int avail = waitAvailable();
        
        // If -1 is returned, the socket was closed
        if (avail == -1) { 
            doClose();
            return -1;
        }
 
        // Check if there is more/less available than we need
        int toRead = avail < len ? avail : len; 
            
        // Check if the buffer will wrap during the read
        if (startRead + toRead <= buffer.length) { 
            // all the data can be read in one go!
            System.arraycopy(buffer, startRead, b, off, toRead);
            startRead = (startRead + toRead) % buffer.length;
        } else { 
            // the buffer wraps, so read the data in two parts
            int part = buffer.length - startRead; 
            System.arraycopy(buffer, startRead, b, off, part);
            System.arraycopy(buffer, 0, b, off+part, toRead-part);
            startRead = toRead-part;
        }
        
        decreaseAvailableAndACK(toRead);
        
        return toRead;               
    }
    
    private void decreaseAvailableAndACK(int amount) throws IOException { 
        
        synchronized (this) {
            available -= amount;
        }
        
        pendingACK += amount;
        
        if (pendingACK > MINIMAL_ACK_SIZE) { 
            parent.sendACK(pendingACK);
            pendingACK = 0;
        }
    }
    
    private synchronized int waitAvailable() throws IOException { 
        
        // shortcut 
        if (available > 0) { 
            return available;
        }
        
        long deadline = 0;
        long timeleft = parent.getSoTimeout();
        
        if (timeleft > 0) { 
            deadline = System.currentTimeMillis() + timeleft;
        } else { 
            timeleft = 0;
        }
        
        while (available == 0) {
            
            if (closePending || closed) { 
                return -1;
            }
            
            try {
                wait(timeleft);                
            } catch (InterruptedException e) {
                // ignore
            }
                        
            if (deadline > 0 && available == 0) { 
                timeleft = deadline - System.currentTimeMillis(); 
            
                if (timeleft <= 0) { 
                    throw new SocketTimeoutException("Timeout while reading " +
                            "data");
                }
            }
        }
        
        return available;        
    }
    
    public synchronized int available() { 
        return available;
    }
    
    public synchronized void close() { 
        closePending = true;
        
        // Wakeup anyone waiting for data
        if (available == 0) { 
            notifyAll();
        }
    }
    
    private synchronized void doClose() { 
        closed = true;
    }
       
    public boolean closed() { 
        return closed;
    }
    
    protected final synchronized void add(int len, DataInputStream dis) throws IOException {
       
        // If the flow control is working correctly, we can alway write the 
        // data here!!!!
        
        // Sanity check -- remote ASAP
        if (len > (buffer.length - available)) { 
            System.err.println("EEK: buffer overflow!!");
        }
        
        int cont = (buffer.length - startWrite);
        
        if (cont >= len) { 
            // We can read the data in one go.
            dis.readFully(buffer, startWrite, len);
            startWrite = (startWrite + len) % buffer.length;
        } else {         
            // The buffer will wrap, so read in two parts
            dis.readFully(buffer, startWrite, cont);
            dis.readFully(buffer, 0, len-cont);
            startWrite = len-cont;
        }
        
        available += len;
        
      //  System.err.println("Buffer has " + available + " bytes...");
        
        // Check if anyone could have been waiting for us...
        if (available == len) { 
            notifyAll();
        }
    }
}
