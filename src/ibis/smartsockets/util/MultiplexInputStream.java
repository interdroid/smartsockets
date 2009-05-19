package ibis.smartsockets.util;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.util.LinkedList;

public class MultiplexInputStream extends InputStream {

    private final int stream;
    
    private LinkedList<byte []> buffers = new LinkedList<byte []>();       
    private byte [] buffer;
    
    private int offset;
    private int length;
    
 //   private boolean eos = false;
    private boolean closed = false;
        
    private int timeout;
    
    private final MultiplexStreamFactory owner;
    
    MultiplexInputStream(MultiplexStreamFactory owner, int stream) {
        this.owner = owner;
        this.stream = stream;
    }
    
    public synchronized void setTimeout(int timeout) { 
        this.timeout = timeout;
    }
    
    public synchronized void addBuffer(byte [] buf) {         
        buffers.add(buf);
        notifyAll();                       
    }
       
    private synchronized byte [] nextBuffer(boolean block) throws IOException {
        
        while (buffers.size() == 0) { 
            
            if (!block) { 
                return null;
            } 
            
            try { 
                wait(timeout);
            } catch (InterruptedException e) {
                // ignore
            }
            
            if (timeout > 0 && buffers.size() == 0) {
                // A timout has occurred
                throw new SocketTimeoutException("Timeout occurred while " 
                        + "waiting for data");
            }
        }
        
        return buffers.removeFirst();
    }
    
    private void getBuffer() throws IOException { 
        
        if (closed) { 
            throw new IOException("Stream already closed");
        }
                        
        if (offset == length) { 
        
            if (buffer != null) {
                owner.returnBuffer(buffer, stream); 
                buffer = null;
            } 
        
            buffer = nextBuffer(true);
            
            if (buffer == null) { 
                throw new EOFException("Trying to read past the end of stream");
            }
            
            offset = 8;
            length = MultiplexStreamFactory.readLength(buffer);                        
        }
    }
    
    /* (non-Javadoc)
     * @see java.io.InputStream#read()
     */
    public int read() throws IOException {        
        getBuffer();        
        return buffer[offset++];        
    }

    /* (non-Javadoc)
     * @see java.io.InputStream#available()
     */
    public synchronized int available() throws IOException {
        
        if (closed) { 
            throw new IOException("Stream already closed");
        }
                
        if (buffer == null) { 
            return 0;
        } else { 
            return buffer.length - offset;
        }
    }

    /* (non-Javadoc)
     * @see java.io.InputStream#close()
     */
    public void close() throws IOException {
        
        closed = true;                  

        // Return any buffers we may still have ...
        if (buffer != null) {
            owner.returnBuffer(buffer, stream); 
        } 
        
        owner.deleteInputStream(stream);
        
        if (buffers.size() != 0) {
            
            byte [] buf = nextBuffer(false);
            
            while (buf != null) { 
                owner.returnBuffer(buf, stream);
                buf = nextBuffer(false);
            }
                        
            throw new IOException("Closing stream that still contains data!");
        }                    
    }

    /* (non-Javadoc)
     * @see java.io.InputStream#mark(int)
     */
    public void mark(int readlimit) {
        // not implemented!!
    }

    /* (non-Javadoc)
     * @see java.io.InputStream#markSupported()
     */
    public boolean markSupported() {
        return false;
    }

    /* (non-Javadoc)
     * @see java.io.InputStream#read(byte[], int, int)
     */
    public int read(byte[] b, int off, int len) throws IOException {

        getBuffer();       
        
        int leftOver = (len < (length-offset)) ? len : (length-offset);        
        System.arraycopy(buffer, offset, b, off, leftOver);
        offset += leftOver;
        
      //  System.out.println("Length = " + length + " Offset = " + offset);
        
        return leftOver;
    }

    /* (non-Javadoc)
     * @see java.io.InputStream#read(byte[])
     */
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    /* (non-Javadoc)
     * @see java.io.InputStream#reset()
     */
    public void reset() throws IOException {
        // not implemented!!
    }

    /* (non-Javadoc)
     * @see java.io.InputStream#skip(long)
     */
    public long skip(long n) throws IOException {
        
        while (n-- > 0) { 
            read();
        }        
        return n;
    }
}
