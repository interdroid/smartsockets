package test.virtual.randomsteal;

import ibis.smartsockets.virtual.InitializationException;
import ibis.smartsockets.virtual.VirtualServerSocket;
import ibis.smartsockets.virtual.VirtualSocket;
import ibis.smartsockets.virtual.VirtualSocketAddress;
import ibis.smartsockets.virtual.VirtualSocketFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Random;

public class Client {
    
    public final static byte OPCODE_PING = 101;
    public final static byte OPCODE_PONG = 102;

    private static int SERVER_TIMEOUT = 60000;
    
    private final VirtualSocketFactory sf;    
    
    // connection to the server
    private final VirtualSocket s; 
    private final DataInputStream in; 
    private final DataOutputStream out;
    
    // for incoming connections
    private final VirtualServerSocket ss;
    private boolean done = false;
        
    private final int clientTimeout; 
    
    private class AcceptThread extends Thread { 

        public void run() { 
         
            try { 
                ss.setSoTimeout(10000);

                while (!getDone()) { 
                    doAccept();
                }

                ss.close();
            
            } catch (Exception e) {
                throw new Error("Accept failed!", e);
            }
        }        
    }
        
    public Client(VirtualSocketAddress target, int timeout) throws IOException, InitializationException { 
        
        this.clientTimeout = timeout;
        
        sf = VirtualSocketFactory.createSocketFactory();
        s = sf.createClientSocket(target, SERVER_TIMEOUT, true, null);
        s.setTcpNoDelay(true);
        
        in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
        out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
                
        ss = sf.createServerSocket(0, 0, null);
        
        s.setSoTimeout(SERVER_TIMEOUT);    
        
        ss.getLocalSocketAddress().write(out);
        out.flush();        
    }
    
    private static void configure(VirtualSocket s) throws SocketException { 
 
        /*
        s.setSendBufferSize(1024*1024);
        s.setReceiveBufferSize(1024*1024);
        */
        
        s.setTcpNoDelay(true);
 
        /*
        System.out.println("Configured socket: ");         
        System.out.println(" sendbuffer     = " + s.getSendBufferSize());
        System.out.println(" receiverbuffer = " + s.getReceiveBufferSize());
        System.out.println(" no delay       = " + s.getTcpNoDelay());
        */        
    }

    public synchronized boolean getDone() {
        return done;
    }
    
    private void doAccept() throws IOException { 
        
        VirtualSocket s;
        
        try {             
            s = ss.accept();
        } catch (SocketTimeoutException e) {
            // allowed
            return;
        }

        InputStream in = null;
        OutputStream out = null;
      
        try { 
            configure(s);
                    
            in = s.getInputStream();
            out = s.getOutputStream();

            int request = in.read(); 
            
            if (request != OPCODE_PING) {
                throw new Error("Client accept received junk!" + request);                
            }
            
            out.write(OPCODE_PONG);
            out.flush();
            
        } catch (Exception e) {
            throw new Error("Client accept failed!", e);            
        } finally { 
            VirtualSocketFactory.close(s, out, in);
        }
    }
    
    private void doConnect(VirtualSocketAddress target) { 
        
        VirtualSocket s = null;
        InputStream in = null;
        OutputStream out = null;
        
        try { 
            s = sf.createClientSocket(target, clientTimeout, true, null);
        
            configure(s);
                    
            in = s.getInputStream();
            out = s.getOutputStream();

            out.write(OPCODE_PING);
            out.flush();
            
            int result = in.read(); 
            
            if (result != OPCODE_PONG) { 
                throw new Error("Client connect received junk!" + result);                
            }            
        } catch (Exception e) {
            throw new Error("Client connect failed!", e);            
        } finally { 
            VirtualSocketFactory.close(s, out, in);
        }
    }
    
    private boolean syncWithServer(int time) throws IOException {
        out.writeByte(Server.OPCODE_SYNC);
        out.writeInt(time);
        out.flush();      
        
        return (in.readByte() == Server.OPCODE_START);         
    }
    
    public void start() throws IOException { 
        
        Random random = new Random();
        
        int count = in.readInt();
        
        // Note: this is without this client, so it's actually clients-1.
        int clients = in.readInt();  
        
        VirtualSocketAddress [] addresses = new VirtualSocketAddress[clients];
        
        for (int i=0;i<clients;i++) { 
            addresses[i] = new VirtualSocketAddress(in);
        }
        
        new AcceptThread().start(); 
        
        int result = in.readByte(); 
        
        if (result != Server.OPCODE_START) { 
            throw new Error("Client received junk from server!" + result);                
        }
        
        boolean cont = true;
        
        while (cont) { 
            
            long start = System.currentTimeMillis();
            
            for (int c=0;c<count;c++) { 
                doConnect(addresses[random.nextInt(clients)]);
            }
            
            long end = System.currentTimeMillis();
            
            cont = syncWithServer((int)(end-start));
        }

        synchronized (this) {
            done = true;
        }

        VirtualSocketFactory.close(s, out, in);
    }
}
