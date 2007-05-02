package test.virtual.alltoall;

import ibis.smartsockets.virtual.VirtualServerSocket;
import ibis.smartsockets.virtual.VirtualSocket;
import ibis.smartsockets.virtual.VirtualSocketAddress;
import ibis.smartsockets.virtual.VirtualSocketFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;



public class Client {

    static final int TIMEOUT   = 5000; 
    static final int MAX_TRIES = 5; 
    static final int MAX_WAIT  = 60000; 
   
    static VirtualServerSocket server;
    static VirtualSocketFactory factory; 
    
    static int rank;
    static int size;
    static VirtualSocketAddress [] others;
         
    static boolean [] outgoing;
    static boolean [] incoming;           
    static Exception [] outgoingExceptions;
        
    static int outgoingCount = 0;
    
    private static class AcceptThread extends Thread { 
    
        int count = 0;
        boolean quit = false;
                
        synchronized boolean done() { 
            return (count == others.length-1);
        }
        
        synchronized void quit() { 
            quit = true;
        }
                
        public void run() { 

            VirtualSocket s = null;
            
            DataInputStream in = null;
            DataOutputStream out = null;
                        
            boolean stop = false;
            
            while (!stop && count < others.length-1) {             
                try {
                    server.setSoTimeout(5000);
                    s = server.accept();
                    
                    in = new DataInputStream(s.getInputStream());
                    out = new DataOutputStream(s.getOutputStream());
                    
                    int src = in.readInt();                    
                    
                    out.writeInt(rank);
                    out.flush();
        
                    if (!incoming[src]) { 
                        incoming[src] = true;
                   
                        synchronized (this) {
                            count++;
                        }
                        
                        System.out.println("AcceptThread: connection from " 
                                + src + " (NEW)");
                    } else {  
                        System.out.println("AcceptThread: connection from " 
                                + src + " (DOUBLE)");                        
                    }     
                } catch (Exception e) {
                    System.out.println("AcceptThread: exception " + e);
                    e.printStackTrace();
                } finally {                     
                    close(s, out, in);
                    s = null;
                    in = null;
                    out = null;
                }
                
                synchronized (this) {
                    stop = quit;
                }                
            }
        }        
    } 
    
    static void close(VirtualSocket s, OutputStream out, InputStream in) { 
        
        try { 
            out.close();                
        } catch (Exception e) {
            // ignore
        }
        
        try { 
            in.close();                
        } catch (Exception e) {
            // ignore
        }
        
        try { 
            s.close();                
        } catch (Exception e) {
            // ignore
        } 
    }
    
    private static void getPoolFromServer(VirtualSocketAddress a) throws IOException { 
        
        System.out.println("Connecting to server " + a);
                 
        VirtualSocket s = factory.createClientSocket(a, TIMEOUT, null);
        
        System.out.println("Got connection");
        
        DataInputStream in = new DataInputStream(s.getInputStream());
        DataOutputStream out = new DataOutputStream(s.getOutputStream());               
        
        System.out.println("Writing my address");
        
        out.writeUTF(server.getLocalSocketAddress().toString());
        out.flush();
        
        System.out.println("Waiting for reply");
                    
        rank = in.readInt();
        
        System.out.println("My rank: " + rank);
                    
        size = in.readInt();
        
        System.out.println("Total machines: " + size);
                    
        others = new VirtualSocketAddress[size];
        outgoing = new boolean[size];
        incoming = new boolean[size];
                  
        Arrays.fill(outgoing, false);
        Arrays.fill(incoming, false);
    
        outgoingExceptions = new Exception[size];
                
        for (int i=0;i<size;i++) { 
            others[i] = new VirtualSocketAddress(in.readUTF());                
            System.out.println("Machine " + i + " -> " + others[i]);                
        }

        System.out.println("Closing connection to server");
        
        close(s, out, in);
    } 
    
    private static void connectTo(int i) { 
      
        VirtualSocket s = null;
        
        DataInputStream in = null;
        DataOutputStream out = null;
                          
        try {
            System.out.println("Setting up connection to " + others[i]);    
                        
            s = factory.createClientSocket(others[i], TIMEOUT, null);
            
            in = new DataInputStream(s.getInputStream());
            out = new DataOutputStream(s.getOutputStream());
            
            out.writeInt(rank);
            out.flush();
                        
            int src = in.readInt();
            
            if (src == i) {
                outgoing[i] = true;
                System.out.println("Connection to " + others[i] + " OK!");
                outgoingCount++;
            } else { 
                System.out.println("Connection to " + others[i] + " failed! " 
                        + "Got " + src + " instead!");
                outgoingExceptions[i] = new Exception("Wrong receiver");
            }                                     
        } catch (Exception e) {
            System.out.println("Connection to " + i + " failed! Got exception " 
                    + e);
            outgoingExceptions[i] = e;
        } finally { 
            close(s, out, in);
        }        
    } 
    
    private static void connectToOthers() { 
        
        int tries = 0;
        
        while (outgoingCount < others.length-1 && tries < MAX_TRIES) { 
        
            System.out.println("Connection to other machines (try " + tries 
                    + " of " + MAX_TRIES + ")");
                        
            for (int i=0;i<others.length;i++) { 
                if (i != rank && !outgoing[i]) {
                    connectTo(i);                
                }                                    
            }        
            
            System.out.println(outgoingCount + " of " + (others.length-1) 
                    + " succeeded"); 
            
            tries++;
        }        
    }
       
    private static void printResults() { 
      
        System.out.println();
        System.out.println();
        System.out.println("-----------------------------------------");
        System.out.println();
        System.out.println("Machine: " + server.getLocalSocketAddress());
        System.out.println();        
        System.out.println("Rank: " + rank);
        System.out.println("Size: " + size);
        System.out.println();        
        System.out.println("End result of connection tests: ");
        
        for (int i=0;i<others.length;i++) { 
            
            if (i != rank) { 
                
                boolean ok = outgoing[i] && incoming[i];
                
                System.out.println(i + ": " + others[i] + " " 
                        + (ok ? "ok" : "failed"));
                
                if (outgoing[i]) { 
                    System.out.println(" out - OK");                        
                } else { 
                    Exception e = outgoingExceptions[i];
                    
                    System.out.println(" out - FAILED (" 
                            + (e == null ? "?" : e.toString()) + ")");
                }
                                
                if (incoming[i]) { 
                    System.out.println(" in - OK");                        
                } else { 
                    System.out.println(" in - FAILED");
                }
                
                System.out.println();                
            } 
        }        
        
        System.out.println();        
    } 
    
    public static void main(String[] args) {

        if (args.length != 1) { 
            System.err.println("Usage java test.alltoall.Client <server>");
            System.exit(1);
        }
        
        try {
            System.out.println("Client started");

            factory = VirtualSocketFactory.createSocketFactory();
            server = factory.createServerSocket(0, 50, null);
           
            System.out.println("Created server socket " 
                    + server.getLocalSocketAddress());
                        
            getPoolFromServer(new VirtualSocketAddress(args[0]));
            
            AcceptThread a = new AcceptThread();            
            a.start();
            
            System.out.println("Connection to others...");
            
            connectToOthers();
            
            System.out.println("Done connection to others, waiting for " +
                    "incoming connections...");
            
            int sleep = 0;
            
            while (!a.done() && sleep < MAX_WAIT) { 
                try {
                    sleep += 5000;
                    Thread.sleep(5000);                     
                } catch (Exception e) {
                    // ignore
                }
            }

            a.quit();

            printResults();            
            
        } catch (Exception e) {
            System.err.println("Client got exception " + e);
            e.printStackTrace(System.err);
        }
    }

}
