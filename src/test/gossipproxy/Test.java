package test.gossipproxy;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedList;

import ibis.connect.gossipproxy.GossipProxyClient;
import ibis.connect.virtual.VirtualServerSocket;
import ibis.connect.virtual.VirtualSocket;
import ibis.connect.virtual.VirtualSocketAddress;
import ibis.connect.virtual.VirtualSocketFactory;

public class Test {

    private static final int DEFAULT_PORT = 14567;
    
    VirtualSocketFactory factory;
    VirtualServerSocket ss; 
    
    GossipProxyClient gpc; 
            
    boolean connected = false;
    
    VirtualSocketAddress target = null;        
    VirtualSocket s = null;                
    DataInputStream in = null;
    DataOutputStream out = null;
    
    
    private Test(int port, LinkedList proxies) throws IOException {         
        factory = VirtualSocketFactory.getSocketFactory();        
        ss = factory.createServerSocket(port, 10, false, null);        
        gpc = new GossipProxyClient(ss.getLocalSocketAddress()); 
        
        while (proxies.size() > 0) { 
            gpc.addProxy((VirtualSocketAddress) proxies.removeFirst());
        }
    }
    
    private void bounce() {
    
        VirtualSocket s = null;                
        DataInputStream in = null;
        DataOutputStream out = null;
        
        try { 
            s = ss.accept();                
            
            in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
        
            out.writeUTF(ss.toString());
            
            String conn = in.readUTF();
            
            System.out.println("Got connection from " + conn + " bouncing...");

            while (true) { 
                
                String tmp = in.readUTF();
                
                out.writeUTF(tmp);
                out.flush();                    

                if (tmp == null) { 
                    return;
                }
            }
            
        } catch (Exception e) {
            System.out.println("Lost connection " + e);
        } finally { 
            VirtualSocketFactory.close(s, out, in);
        }
    }
    
    private void connect(String target) { 
        
    }
    
    private void disconnect() { 
        
    }
    
    private void send(String line) { 
        
    }
    
    private void parseInput() { 
        
        boolean done = false;
                        
        BufferedReader clin = new BufferedReader(new InputStreamReader(System.in));
        
        try {         
            while (!done) { 
                System.out.print("> ");
                System.out.flush(); 
                
                String line = clin.readLine();
                
                if (line.startsWith("connect ")) {                    
                    if (connected) { 
                        System.out.println("Already connected to " + target);
                    } else {                    
                        connect(line.substring(8).trim());                        
                    } 
                } else if (line.startsWith("disconnect")) {
                    if (!connected) { 
                        System.out.println("Not connected yet!");
                    } else {                    
                        disconnect();
                    }                  
                } else if (line.startsWith("exit")) {                    
                    if (connected) { 
                        disconnect();
                    }
                    done = true;
                } else { 
                    if (!connected) {
                        System.out.println("Not connected yet!");
                    } else { 
                        send(line);
                    } 
                }
            }
        } catch (Exception e) {
            System.out.println("Got exception! " + e);
        } finally { 
            VirtualSocketFactory.close(s, out, in);
        }
    }
        
    private void start(boolean interactive) { 
        
        if (!interactive) { 
            bounce();            
        } else { 
            parseInput();
        }        
    }
    
    public static void main(String [] args) { 
        
        int port = DEFAULT_PORT;
        boolean interactive = false;                
        LinkedList proxies = new LinkedList();
        
        for (int i=0;i<args.length;i++) { 
            if (args[i].equals("-port")) { 
                port = Integer.parseInt(args[++i]);                
            } else if (args[i].equals("-interactive")) { 
                interactive = true;
            } else if (args[i].equals("-proxy")) {
                try { 
                    proxies.add(new VirtualSocketAddress(args[++i]));
                } catch (Exception e) {
                    System.err.println("Failed to parse proxy: " 
                            + args[i] + " (ignoring)");
                }
            } else { 
                System.err.println("Unknown option: " + args[i]);
                System.exit(1);
            }           
        }
        
        if (!interactive && proxies.size() == 0) { 
            System.err.println("No proxies specified!");
            System.exit(1);        
        }        
        
        try { 
            new Test(port, proxies).start(interactive);
        } catch (Exception e) { 
            System.err.println("EEK " + e);
            System.exit(1);
        }
    }    
}
