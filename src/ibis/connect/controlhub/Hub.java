package ibis.connect.controlhub;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;

import org.apache.log4j.Logger;

import ibis.connect.direct.DirectServerSocket;
import ibis.connect.direct.DirectSocket;
import ibis.connect.direct.DirectSocketFactory;
import ibis.connect.direct.SocketAddressSet;

public class Hub extends Thread implements Protocol {
   
    private static final int TIMEOUT = 5000;
    private static final int DEFAULT_PORT = 9987;
    
    static Logger logger = 
        ibis.util.GetLogger.getLogger(Hub.class.getName());
    
    private final DirectSocketFactory directFactory;    
    private final DirectServerSocket server; 
    
    private final HashMap connections = new HashMap(); 
    
    private class Connection extends Thread { 
    
        private final String src;
        private final DirectSocket s;               
        private final DataOutputStream out; 
        private final DataInputStream in;

        private boolean done = false;
        
        Connection(String src, DirectSocket s, DataOutputStream out,
                DataInputStream in) {
            this.src = src;
            this.s = s;
            this.out = out;
            this.in = in;
        }
        
        private void handleMessage() throws IOException { 
            // Handle the message
            String target = in.readUTF();                    
            String module = in.readUTF();
            int code = in.readInt();
            String message = in.readUTF();
            
            if (logger.isDebugEnabled()) { 
                logger.debug("Incoming message: [" + target + ", " 
                        + module + ", " + code + ", " + message); 
            } 

            Connection c = getConnection(target);
            
            if (c == null) { 
                logger.info("Failed to forward message to " + target + 
                        ": unknown target");
                // TODO: reply ?                         
                return;            
            }
                                
            boolean result = c.sendMessage(src, module, code, message);
            // TODO: reply ?             
        } 
                
        private void disconnect() { 
            done = true;
            removeConnection(src);
            DirectSocketFactory.close(s, out, in);            
        } 
    
        private void receive() {            
            try { 
                int opcode = in.read();

                switch (opcode) { 
                case MESSAGE:
                    if (logger.isDebugEnabled()) {
                        logger.debug("Connection " + src + " got message");
                    }                     
                    handleMessage();
                    break;
                case DISCONNECT:
                    if (logger.isDebugEnabled()) {
                        logger.debug("Connection " + src + " disconnecting");
                    } 
                    disconnect();
                    break;
                default:
                    logger.warn("Connection " + src + " got unknown opcode" 
                            + opcode + " -- disconnecting");
                    disconnect();
                } 
            } catch (Exception e) { 
                logger.warn("Connection to " + src + " is broken!", e);
                DirectSocketFactory.close(s, out, in);
            }
        }
        
        synchronized boolean sendMessage(String src, String module, int code, 
                String message) {  
            
            try{ 
                out.write(MESSAGE);
                out.writeUTF(src);
                out.writeUTF(module);
                out.writeInt(code);
                out.writeUTF(message);
                out.flush();
                return true;
            } catch (IOException e) {
                logger.warn("Connection " + src + " is broken!", e);
                DirectSocketFactory.close(s, out, in);
                return false;                
            }
        }
        
        public void run() {            
            while (!done) { 
                receive();
            }                                    
        }
    } 
    
    private Hub(int port) throws IOException { 
        
        logger.info("Creating hub on port: " + port);
        
        try {         
            directFactory = DirectSocketFactory.getSocketFactory();
            server = directFactory.createServerSocket(port, 50, null);        
        
            System.err.println("Hub created on host: \"" 
                    + server.getAddressSet() + "\"");
        } catch (IOException e) { 
            logger.info("Failed to creating hub on port: " + port, e);            
            throw e;
        }        
    }
                
    public SocketAddressSet getAddress() { 
        return server.getAddressSet();
    }
    
    private synchronized void addConnection(String key, Connection c) { 
        connections.put(key, c);
    }

    private synchronized Connection getConnection(String key) { 
        return (Connection) connections.get(key);
    }

    private synchronized void removeConnection(String key) { 
        connections.remove(key);
    }
    
    private void handleConnect(DirectSocket s, DataInputStream in, 
            DataOutputStream out) { 

        try { 
            String src = in.readUTF();
                
            if (getConnection(src) != null) { 
                if (logger.isDebugEnabled()) { 
                    logger.debug("Hub incoming connection from " + src + 
                            " refused"); 
                } 
            
                out.write(CONNECT_REFUSED);
                out.flush();
                DirectSocketFactory.close(s, out, in);
                return;
            }
        
            if (logger.isDebugEnabled()) { 
                logger.debug("Hub incoming connection from " + src 
                        + " accepted"); 
            } 

            out.write(CONNECT_ACCEPTED);
            out.flush();
            
            Connection c = new Connection(src, s, out, in);
            addConnection(src, c);                                               
            c.start();
            
        } catch (IOException e) { 
            logger.warn("Hub got exception while handling connect!", e);
            DirectSocketFactory.close(s, out, in);
        }            
    }    
    
    public void run() { 
      
        while (true) {
            
            DirectSocket s = null;
            
            DataInputStream in = null;
            DataOutputStream out = null;
                        
            if (logger.isDebugEnabled()) { 
                logger.debug("Hub accepting connection");
            } 
            
            try { 
                s = server.accept();
                
                if (logger.isDebugEnabled()) { 
                    logger.debug("Hub got connection from " + 
                            s.getRemoteSocketAddress());
                } 
                                
                in = new DataInputStream(s.getInputStream());
                out = new DataOutputStream(s.getOutputStream());
                
                int opcode = in.read();
                
                if (opcode == CONNECT) {
                    handleConnect(s, in, out);                                        
                } else {
                    logger.warn("Hub received unknown opcode " + opcode 
                            + " from " + s.getRemoteSocketAddress());                  
                    DirectSocketFactory.close(s, out, in);
                } 
                
            } catch (Exception e) {
                DirectSocketFactory.close(s, out, in);
                System.out.println("Incoming connection failed!");
            }
        }        
    }

    public static Hub createHub(int port) throws IOException { 
        logger.info("Creating hub on port: " + port);
            
        Hub h = new Hub(port);
        h.setDaemon(true);
        h.start();
        return h;
    
    }
    
    public static Hub createHub() throws IOException {
        return createHub(DEFAULT_PORT);
    }
        
    public static void main(String [] args) { 
        
        int port = DEFAULT_PORT;
        
        if (args.length > 0)  { 
            port = Integer.parseInt(args[0]);            
        }

        try { 
            createHub(port);
        } catch (Exception e) {
            System.err.println("Failed to start hub: " + e);
        }
    }    
}
