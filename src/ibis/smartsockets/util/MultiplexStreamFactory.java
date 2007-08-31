package ibis.smartsockets.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;

public final class MultiplexStreamFactory {

    private final static int ACK   = 42;
    private final static int DATA  = 43;
    
    private final static int DEFAULT_BUFFER_SIZE = 8 + 16*1024;    
    private final static int DEFAULT_CREDITS = 16;
    
    private final int bufferSize;
    
    private final InputStream in;        
    private final OutputStream out;
        
    private final HashMap<Integer, MultiplexInputStream> inputs = 
        new HashMap<Integer, MultiplexInputStream>();
    
    private final HashMap<Integer, MultiplexOutputStream> outputs = 
        new HashMap<Integer, MultiplexOutputStream>();
    
    private final InputReader reader;
                
    private boolean closed = false;
    
    private class InputReader extends Thread { 
                              
        private final int read(InputStream in, byte [] buffer, int offset, 
                int len) throws IOException { 
         
            int read = 0;
            
            while (read < len) { 
                int bytes = in.read(buffer, offset+read, len-read);
                
                if (bytes == -1) { 
                    return read;
                } else { 
                    read += bytes;
                }
                
               // System.out.println("Read " + read);
            }                        

            return read;            
        }
        
        public void run() { 
            
            try { 
                byte [] buffer = null;            
                int read = 0;
                int length = 0;
                int target = 0;
                int opcode = 0;
                
                while (true) {
                                       
                    if (buffer == null) { 
                        buffer = getBuffer();
                    } 
                    
                   // System.out.println("Reader: Waiting for data...");
                                        
                    // We should read 8 bytes here.
                    // 1 byte  - opcode
                    // 3 bytes - target
                    // 4 bytes - buffer length                        
                    read = read(in, buffer, 0, 8); 
                
                    if (read != 8) {
                        // end of stream ?
                        System.out.println("Reader: Stream closed");
                        return;
                    }
                    
                    opcode = readOpcode(buffer);
                    target = readStream(buffer);
                    length = readLength(buffer);
                                        
                    switch (opcode) { 
                    case ACK:
                       // System.out.println("Reader: Got ACK");
                        deliverACK(target, length);
                        break;                       
                    
                    case DATA:
                        // read the rest of the data and deliver the buffer
                        //System.out.println("Reader: Got DATA");
                        read(in, buffer, read, length-read);
                        deliverBuffer(target, buffer);
                        buffer = null;                    
                        break;

                    default:  
                        System.out.println("Reader: Got UNKNOWN OPCODE!");
                        // TODO: print error                        
                    } 
                }   
            } catch (IOException e) {
                
                if (closed) { 
                    System.out.println("Reader: Stream closed");
                    return;                    
                } else { 
                    System.out.println("Reader: got exception " + e);
                    // TODO: handle exception ? 
                } 
            }
        } 
    }
               
    public MultiplexStreamFactory(InputStream in, OutputStream out) { 
        this(in, out, DEFAULT_BUFFER_SIZE);
    }    
    
    public MultiplexStreamFactory(InputStream in, OutputStream out, 
            int bufferSize) {
        
        this.in = in;
        this.out = out;
        this.bufferSize = bufferSize;
        
        outputs.put(Integer.valueOf(0), new MultiplexOutputStream(this, out, 
                0, bufferSize, DEFAULT_CREDITS));

        inputs.put(Integer.valueOf(0), new MultiplexInputStream(this, 0));
        
        reader = new InputReader();
        reader.start();
        
        System.out.println("Factory started");
    }
    
    private final MultiplexInputStream findInput(int target) {
        synchronized (inputs) {
            return inputs.get(Integer.valueOf(target));
        }
    }
    
    private final MultiplexOutputStream findOutput(int target) {
        synchronized (outputs) {
            return outputs.get(Integer.valueOf(target));
        }
    }
        
    final void deliverBuffer(int target, byte [] buffer) throws IOException {
        
        MultiplexInputStream min = findInput(target);
        
        if (min != null) {
            min.addBuffer(buffer);
        } else {
            // TODO: print error using logger !
            System.err.println("Warning received data for unknown stream!");
            returnBuffer(buffer, target);
        }                
    }
       
    final void deliverACK(int target, int credits) {
        
        MultiplexOutputStream min = findOutput(target);
        
        if (min != null) {
            min.addCredits(credits);
        } else {
            // TODO: print error using logger !
            System.err.println("Warning received ack for unknown stream!");
        }                
    }
        
    final byte [] getBuffer() {
        // TODO: create some cache here ? 
        return new byte[bufferSize];        
    }
    
    static final void writeDataOpcode(byte [] buffer) { 
        buffer[0] = (byte)(0xff & DATA);
    }
    
    private static final void writeACK(byte [] buffer, int stream) { 
        buffer[0] = (byte)(0xff & ACK);
        buffer[1] = (byte)(0xff & (stream >> 16));
        buffer[2] = (byte)(0xff & (stream >> 8));
        buffer[3] = (byte)(0xff & stream);
        buffer[4] = (byte)0;
        buffer[5] = (byte)0;
        buffer[6] = (byte)0;
        buffer[7] = (byte)8;
    }
    
    static final void writeStream(byte [] buffer, int stream) { 
        buffer[1] = (byte)(0xff & (stream >> 16));
        buffer[2] = (byte)(0xff & (stream >> 8));
        buffer[3] = (byte)(0xff & stream);        
    }
        
    static final void writeLength(byte [] buffer, int length) { 
        buffer[4]   = (byte)(0xff & (length >> 24));
        buffer[5] = (byte)(0xff & (length >> 16));
        buffer[6] = (byte)(0xff & (length >> 8));
        buffer[7] = (byte)(0xff & length);        
    }
    
    static final int readOpcode(byte [] buffer) {    
        return (buffer[0] & 0xff);
    }
        
    static final int readLength(byte [] buffer) {    
        return (((buffer[4] & 0xff) << 24)   | 
                ((buffer[5] & 0xff) << 16) |
                ((buffer[6] & 0xff) << 8)  | 
                 (buffer[7] & 0xff));
    }
           
    static final int readStream(byte [] buf) {
        return (((buf[1] & 0xff) << 16) |
                ((buf[2] & 0xff) << 8)  | 
                (buf[3] & 0xff));            
    }   
    
    final void returnBuffer(byte [] buffer, int stream) throws IOException {
        
        writeACK(buffer, stream);
                  
        // use the buffer the write an ACK before returning it        
        synchronized (out) {
            out.write(buffer, 0, 8);
        }
               
        // TODO: create some cache here ?         
    }
        
    final void deleteOutputStream(int number) {        
        synchronized (outputs) {
            outputs.remove(Integer.valueOf(number));
        }   
    }
    
    final void deleteInputStream(int number) {
        synchronized (inputs) {
            inputs.remove(Integer.valueOf(number));
        }
    }
        
    public OutputStream getBaseOut() { 
        return findOutput(0);
    }
    
    public InputStream getBaseIn() { 
        return findInput(0);
    }
 
    public OutputStream getOutputStream(int number) { 
        return findOutput(number);
    }
    
    public InputStream getInputStream(int number) { 
        return findInput(number);
    }
 
    public MultiplexOutputStream createOutputStream(int number) { 
        synchronized (outputs) {
            MultiplexOutputStream mos = 
                new MultiplexOutputStream(this, out, number, bufferSize, 
                        DEFAULT_CREDITS);
            
            outputs.put(Integer.valueOf(number), mos);
            return mos;
        }        
    }
    
    public MultiplexInputStream createInputStream(int number) {
        synchronized (inputs) {
            MultiplexInputStream mis = new MultiplexInputStream(this, number);            
            inputs.put(Integer.valueOf(number), mis);
            return mis;
        }
    }    
    
    public void close() throws IOException { 
     
        synchronized (inputs) {
            if (inputs.size() > 0) { 
                throw new IOException("Attempting to close MultiplexFactory, " 
                        + "but there are " + inputs.size() + " inputs active!");
            }
        } 
        
        synchronized (outputs) {
            if (outputs.size() > 0) { 
                throw new IOException("Attempting to close MultiplexFactory, " 
                        + "but there are " + outputs.size() 
                        + " outputs active!");
            }
        } 
        
        System.out.println("Closing factory");
        
        closed = true;
        
        in.close();
        out.close();                
    }    
}
