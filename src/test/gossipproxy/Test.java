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
        
        while (true) {         
            try { 
                s = ss.accept();                
                
                in = new DataInputStream(
                        new BufferedInputStream(s.getInputStream()));
                out = new DataOutputStream(
                        new BufferedOutputStream(s.getOutputStream()));
                
                out.writeUTF(ss.toString());
                out.flush();
                
                String conn = in.readUTF();
                
                System.out.println("Got connection from " + conn + " bouncing...");

                boolean done = false;
                
                while (!done) { 
                    
                    String tmp = in.readUTF();
                    
                    System.out.println("Bouncer got: " + tmp);
                    
                    out.writeUTF("I got " + tmp);
                    out.flush();                    
                    
                    if (tmp.equals("")) {
                        // empty string equals disconnect!
                        System.out.println("Client disconnected...");
                        done = true;
                    }
                }
                
            } catch (Exception e) {
                System.out.println("Lost connection " + e);
            } finally { 
                VirtualSocketFactory.close(s, out, in);
            }
        } 
    }
    
    private void directConnect(String target) { 
        
        if (connected) { 
            System.out.println("Already connected to " + target);
            return;
        }
        
        try { 
            VirtualSocketAddress address = new VirtualSocketAddress(target);
                        
            s = factory.createClientSocket(address, 10000, null);                
            
            in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
        
            out.writeUTF(ss.toString());            
            String conn = in.readUTF();
            
            System.out.println("Connected to " + conn);

            connected = true;
        } catch (Exception e) {
            System.out.println("Connection setup failed " + e);
            VirtualSocketFactory.close(s, out, in);
        } 
    }

    private void proxyConnect(String target) { 
        
        if (connected) { 
            System.out.println("Already connected to " + target);
            return;
        }
        
        try { 
            VirtualSocketAddress address = new VirtualSocketAddress(target);
                        
            s = gpc.connect(address, 10000);                
            
            in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
        
            String conn = in.readUTF();
            
            System.out.println("Connected to " + conn);

            out.writeUTF(ss.toString());            
            out.flush();
            
            connected = true;
        } catch (Exception e) {
            System.out.println("Connection setup failed " + e);
            VirtualSocketFactory.close(s, out, in);
        } 
    }
    
    private void disconnect() { 
        if (!connected) { 
            System.out.println("Not connected yet!");
            return;
        } 

        try { 
            out.writeUTF("");    
            out.flush();
            
            String tmp = in.readUTF();
            
            if (!tmp.equals("")) {             
                System.out.println("Disconnect returned: " + tmp);
            } 
        } catch (Exception e) {
            System.out.println("Disconnect troublesome: " + e);
        } 
        
        VirtualSocketFactory.close(s, out, in);        
        connected = false;
    }
    
    private void send(String line) { 
        try { 
            if (line == null && line.length() == 0) {
                System.out.println("Nothing to send!");
            } 
            
            out.writeUTF(line);
            out.flush();
            
            String tmp = in.readUTF();
            System.out.println("Reply: " + tmp);                       
            
        } catch (Exception e) {
            System.out.println("Lost connection " + e);
            VirtualSocketFactory.close(s, out, in);
            connected = false;
        }           
    }
    
    private void proxy(String proxy) { 
        
        VirtualSocketAddress address = null;
        
        try { 
            address = new VirtualSocketAddress(proxy);        
        } catch (Exception e) {
            System.out.println("Failed to parse proxy address!");
            return;
        }
        
        if (!gpc.addProxy(address)) { 
            System.out.println("Failed to connect to proxy!");
        }
    }
    
    private void usage() {         
        System.out.println("help         - this help");
        System.out.println("proxy <id>   - add proxy with address <id> to list");
        System.out.println("connect <id> - connect to machine with address <id>");
        System.out.println("disconnect   - disconnect current connection");
        System.out.println("send <txt>   - send text <txt> over connection");        
        System.out.println("exit         - disconnect and exit");        
    }
    
    private void parseInput() { 
        
        boolean done = false;
                        
        BufferedReader clin = new BufferedReader(new InputStreamReader(System.in));
        
        try {         
            while (!done) { 
                System.out.print("> ");
                System.out.flush(); 
                
                String line = clin.readLine().trim();
                
                if (line.startsWith("help")) {
                    usage();
                } else if (line.startsWith("proxy ")) {                    
                    proxy(line.substring(6).trim());                                         
                } else if (line.startsWith("connect ")) {                    
                    proxyConnect(line.substring(8).trim());                         
                } else if (line.startsWith("disconnect")) {
                    disconnect();
                } else if (line.startsWith("send ")) {
                    if (!connected) {
                        System.out.println("Not connected yet!");
                    } else { 
                        send(line.substring(5).trim());
                    }                     
                } else if (line.startsWith("exit")) {                    
                    if (connected) { 
                        disconnect();
                    }
                    done = true;
                } else {
                    System.out.println("Unknown command, try help");
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
