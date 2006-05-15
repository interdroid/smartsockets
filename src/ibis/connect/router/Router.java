package ibis.connect.router;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;

public class Router extends Thread {

    private static int DEFAULT_PORT = 16543; 
    
    private final int port;
    private boolean done = false;
    
    private ServerSocketChannel ssc;
    private Selector selector;
    
    
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
        
        selector = Selector.open();        
        ssc.register(selector, SelectionKey.OP_ACCEPT, ssc);        
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
            sc.register(selector, SelectionKey.OP_READ);            
        } catch (IOException e) {            
            System.err.println("Failed to accept! " + e);
        }
    }
    
    private void handleRead(SocketChannel sc) { 
        
        //sc.read(Bu)
        
    }
        
    private void handleOperation(SelectionKey key) { 
   
        if (key.isAcceptable()) { 
            handleAccept((ServerSocketChannel) key.channel());
        }
        
        if (key.isReadable()) { 
            handleRead((SocketChannel) key.channel());
        }
        
        
        
    }
    
    public void run() { 

        try { 
            while (true) { 
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
            // TODO: handle exception
        }        
    }    
}
