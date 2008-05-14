package ibis.smartsockets.util;

import java.io.IOException;
import java.io.OutputStream;

public class CountingOutputStream extends OutputStream {

    private static int streamCount = 0;
    
    private long bytes;
    private long writes;
    
    private final OutputStream out;
    
    private final long start;
    private final long index;
    
    private long lastPrint = 0;
    private long interval = 1000;
    
    private static synchronized int getID() { 
        return streamCount++;
    }
    
    public CountingOutputStream(OutputStream out) { 
        this.out = out;
        this.index = getID();
        start = lastPrint = System.currentTimeMillis();
    }
    
    public long getBytesWritten() { 
        return bytes;
    }
    
    public void write(byte [] b, int off, int len) throws IOException {
        writes++;
        bytes += len;
        out.write(b, off, len);
   
        long now = System.currentTimeMillis();
        
        if (now-lastPrint > interval) { 
            
            double time = (now-start)/1000.0;
            
            System.out.printf("%d %.2f %d %d\n", index, time, writes, bytes);
            lastPrint = now;
        }
    }
    
    public void write(byte [] b) throws IOException {
        writes++;
        bytes += b.length;
        out.write(b);     
        long now = System.currentTimeMillis();
        
        if (now-lastPrint > interval) { 

            double time = (now-start)/1000.0;
            
            System.out.printf("%d %.2f %d %d\n", index, time, writes, bytes);
            lastPrint = now;
        }
    }
    
    public void write(int b) throws IOException {
        writes++;
        bytes++;
        out.write(b);     
   
        long now = System.currentTimeMillis();
        
        if (now-lastPrint > interval) { 
            double time = (now-start)/1000.0;
            System.out.printf("%d %.2f %d %d\n", index, time, writes, bytes);
            lastPrint = now;
        }
    }

    public void flush() throws IOException { 
        out.flush();
    }
    
    public void close() throws IOException { 
        out.close();
    }
     
}
