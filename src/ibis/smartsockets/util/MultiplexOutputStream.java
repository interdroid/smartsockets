package ibis.smartsockets.util;

import java.io.IOException;
import java.io.OutputStream;

public class MultiplexOutputStream extends OutputStream {

    private final OutputStream out; 
    private final int stream; 
    
    private final byte [] buffer;

    private int offset; 
    private boolean closed = false;
        
    private final boolean flowControl; 
    private int credits; 
    
    private final MultiplexStreamFactory owner;
    
    public MultiplexOutputStream(MultiplexStreamFactory owner, OutputStream out,
            int stream, int bufsize, int credits) {
        
        this.owner = owner;
        this.out = out;
        this.stream = stream;
        this.credits = credits;                
        this.buffer = new byte[bufsize];        
    
        flowControl = (credits > 0);
       
        // Write the DATA opcode and stream number into bytes 0...3 of the 
        // buffer and reserve 4 bytes for the length field.
        MultiplexStreamFactory.writeDataOpcode(buffer);       
        MultiplexStreamFactory.writeStream(buffer, stream);        
        offset = 8;
    } 
         
    public void write(int b) throws IOException {
        
        if (closed) { 
            throw new IOException("Stream already closed");
        }
        
        if (offset >= buffer.length) { 
            localFlush();
        }
        
        buffer[offset++] = (byte)(0xff & b);
    }
    
    public void write(byte [] b) throws IOException {
        write(b, 0, b.length);
    }
    
    public void write(byte [] b, int off, int len) throws IOException {

        if (closed) { 
            throw new IOException("Stream already closed");
        }
        
        if (offset >= buffer.length) { 
            localFlush();
        }
        
        while (true) { 
                
            int leftover = buffer.length - offset;
                
            if (len <= leftover) {
                System.arraycopy(b, off, buffer, offset, len);
                offset += len;
               // System.out.println("New offset " + offset);
                return;
            }             
            
            System.arraycopy(b, off, buffer, offset, leftover);
            offset += leftover;            
           // System.out.println("New offset " + offset);
            off += leftover;
            len -= leftover;
            localFlush();            
        }
    }
    
    private synchronized void getCredit() { 
        
        while (credits == 0) { 
            try { 
                wait();
            } catch (InterruptedException e) {
                // ignore
            }            
        }
        credits--;
    }
    
    private void localFlush() throws IOException { 
               
        // Only flush if we have data, or if the stream is closed.        
        if (offset > 8) {          
        
            // Wait for credits if neccesary. 
            if (flowControl) {
                getCredit();
            }
                        
            // Write the size of the buffer to position 4...7.              
            MultiplexStreamFactory.writeLength(buffer, offset);
        
            synchronized (out) {
               // System.out.println("Writing " + offset + " bytes to stream");
                out.write(buffer, 0, offset);
            } 
        
            offset = 8;           
        }
    }
        
    public void flush() throws IOException {        
        localFlush();
        out.flush();
    }
    
    public void close() throws IOException { 
        localFlush();
        out.flush();                
        owner.deleteOutputStream(stream);
        closed = true;
    }   
    
    synchronized void addCredits(int creditsToAdd) {
        if (flowControl) { 
            credits += creditsToAdd;
            notifyAll();
        } 
    }
}
