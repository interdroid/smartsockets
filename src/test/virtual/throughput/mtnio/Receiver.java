/**
 * 
 */
package test.virtual.throughput.mtnio;

import ibis.smartsockets.virtual.VirtualSocket;
import ibis.smartsockets.virtual.VirtualSocketFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

class Receiver extends Thread { 

    private final DataSink d;
    private final VirtualSocket s;    
    private final SocketChannel channel;
    private final ByteBuffer buffer;
    private final ByteBuffer opcode;
    
    private final int size;
    
    Receiver(DataSink d, VirtualSocket s, DataOutputStream out, 
            DataInputStream in, int size) { 
        
        this.d = d;
        this.s = s;
        this.size = size;
        
        channel = s.getChannel();
        
        buffer = ByteBuffer.allocateDirect(size);  
        buffer.clear();
        
        opcode = ByteBuffer.allocateDirect(4);  
        opcode.clear();    
    }
    
    private int readOpcode() {        
        int block = 0;
        
//        System.out.println("Reading opcode");
                        
        try {
            int read = 0;
            
            while (read < 4) { 
                int tmp = channel.read(opcode);
                
                if (tmp == -1) { 
                    throw new EOFException("Socket closed while reading opcode");
                }
                
                read += tmp;
            } 
            
            opcode.flip();
            
            block = opcode.getInt();
            
            opcode.clear();
        } catch (Exception e) {
            System.err.println("Failed to read opcode! " + e);
            e.printStackTrace();
            System.exit(1);
        }
            
        //System.out.println("Read opcode "  + block);
        
        return block;        
    }
    
    private boolean receiveData() { 
        
        int block = -2;
        
        try {
            do {
                //System.out.println("Got block " + block);
                
                block = readOpcode();
                
                if (block >= 0) {
                    
                    int read = 0;
                    
                    while (read < size) {
                    
                        int tmp = channel.read(buffer);
                        
                        if (tmp == -1) { 
                            throw new EOFException("Socket closed while reading opcode");
                        }
                        
                        read += tmp;

//                        System.out.println("Read data " + tmp + " (" + read + ")");
                    }
                    
                    buffer.clear();
                }   
            } while (block >= 0);
        } catch (Exception e) { 
            System.out.println("Failed to read data!" + e);
            e.printStackTrace();
            System.exit(1);
        }

        // TODO: do something with stats ?
        d.done();
        
        return (block == -2);
    }
    
    public void run() { 

        boolean stop = false;
        
        while (!stop) { 
            stop = receiveData();
        }
        
        VirtualSocketFactory.close(s, channel);
    }
}