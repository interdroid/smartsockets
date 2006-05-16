package ibis.connect.router.nio;

import ibis.connect.virtual.VirtualSocketAddress;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;

public class Router extends Thread {

    private static int DEFAULT_PORT = 16543; 
    private static int BUFFER_SIZE = 1024; 
        
    private final int port;
    private boolean done = false;
    
    private ServerSocketChannel ssc;
    private Selector selector;
    
    private class Connection {       
        
        private final SocketChannel sc; 
        VirtualSocketAddress address;        
        byte [] addressInBytes; 
        ByteBuffer readBuffer;
        long bytesRead;
                
        Connection(SocketChannel sc) {
            this.sc = sc;
            readBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        }
        
        void readAddress() { 
            // NOTE: Nasty assumption here is that the buffer is large enough to 
            // contain an int + the address in byte form.                          
            
            if (bytesRead < 4) {  
                // Not enough bytes read to decode the address. Try again later. 
                return; 
            } 
            
            int len = readBuffer.getInt(0); 
                
            if (bytesRead < (4+len)) {
                // Not enough bytes read to decode the address. Try again later. 
                return;
            }
     
            // Enough bytes in the buffer for the address
            readBuffer.position(4);
            
            addressInBytes = new byte[len];       
            readBuffer.get(addressInBytes);
            
            try {
                address = new VirtualSocketAddress(new String(addressInBytes));
            } catch (UnknownHostException e) {
                // TODO Auto-generated catch block
                System.out.println("Oops: got exception during address read!");
                e.printStackTrace();
            }
            
            return;
        }
        
        private void handleRead() { 
            
            int bytes = 0;
            
            try {
                bytes = sc.read(readBuffer);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                System.out.println("Oops: got exception during read!");            
                e.printStackTrace();
            }
            
            if (bytes > 0) {
                bytesRead += bytes;            
                System.err.println("read " + bytes + " bytes from " + toString());
                
                if (address == null) { 
                    // it's a new connection, so first read the address of the 
                    // sender.  
                    readAddress();
                    
                    if (address == null) {
                        // Failed to read adress
                        return;
                    }
                } else {                 
                    // TODO: do something useful with the data here....
                    readBuffer.clear();
                } 
            }
        }
        
        public String toString() { 
            return "Connection from " + address; 
        }
    }
        
    public Router() throws IOException {         
        port = DEFAULT_PORT;        
    
        try { 
            ssc = ServerSocketChannel.open();
            ServerSocket ss = ssc.socket();           
            SocketAddress a = new InetSocketAddress(port);            
            ss.bind(a);
        } catch (IOException e) { 
            System.out.println("Failed to init Router: " + e);
            throw e;
        }

        ssc.configureBlocking(false);

        selector = Selector.open();        
        ssc.register(selector, SelectionKey.OP_ACCEPT, ssc);
        
        System.err.println("Created router on: " + ssc);
    } 
        
    private final synchronized boolean getDone() { 
        return done;
    }
    
    public synchronized void done() { 
        done = true;
    }
            
    private void handleAccept(ServerSocketChannel ssc) { 
    
        try { 
            SocketChannel sc = ssc.accept();        
            sc.register(selector, SelectionKey.OP_READ, new Connection(sc));            
        } catch (IOException e) {            
            System.err.println("Failed to accept! " + e);
        }
    }
     
            
    private void handleOperation(SelectionKey key) { 
   
        if (key.isAcceptable()) { 
            handleAccept((ServerSocketChannel) key.channel());
        }
        
        if (key.isReadable()) {            
            ((Connection) key.attachment()).handleRead();
        }
    }
    
    public void run() { 

        try { 
            while (!getDone()) { 
                int ready = selector.select();
                
                if (ready > 0) { 
                    Set selectedKeys = selector.selectedKeys();

                    Iterator i = selectedKeys.iterator();
                    
                    while (i.hasNext()) {                         
                        handleOperation((SelectionKey) i.next());
                        i.remove();
                    }                    
                }
            }
        } catch (Exception e) {
            System.out.println("oops: " + e);
            // TODO: handle exception
        }        
    }    
}
