package test.virtual.chain;


import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import smartsockets.virtual.VirtualServerSocket;
import smartsockets.virtual.VirtualSocket;
import smartsockets.virtual.VirtualSocketAddress;
import smartsockets.virtual.VirtualSocketFactory;

public class Client {

    static int datasize = 32*1024;
    static int headersize = 166;
    static int count = 1024;
    static int repeat = 10;
    static String servername = null;       
    
    static boolean useRealloc = false;
    static boolean useHeader = false;
    
    static final int TIMEOUT   = 5000; 
    static final int MAX_TRIES = 5; 
    static final int MAX_WAIT  = 60000; 
   
    static VirtualServerSocket server;
    static VirtualSocketFactory factory; 
    
    static int rank;
    static int size;
    static VirtualSocketAddress [] others;
               
    static VirtualSocket nextMachine;
    static OutputStream out;
    
    static VirtualSocket prevMachine;
    static InputStream in;
        
    static int outgoingCount = 0;
        
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
                           
        for (int i=0;i<size;i++) { 
            others[i] = new VirtualSocketAddress(in.readUTF());                
            System.out.println("Machine " + i + " -> " + others[i]);                
        }

        System.out.println("Closing connection to server");
        
        close(s, out, in);
    } 
    
    private static void connectToNext() { 
        
        if (rank != size-1) {
            try {            
                nextMachine = factory.createClientSocket(others[rank+1], TIMEOUT, null);                
                out = nextMachine.getOutputStream();
                
                System.out.println("Created connection to next");
            } catch (Exception e) {
                System.out.println("Connection to next failed! " + e);
                e.printStackTrace();
            }
        }
    }

    private static void connectToPrev() { 
        if (rank != 0) {         
            try {
                server.setSoTimeout(5000);
                prevMachine = server.accept();            
                in = prevMachine.getInputStream();

                System.out.println("Got connection from prev");                
            } catch (Exception e) {
                System.out.println("Connection from prev failed! " + e);
                e.printStackTrace();
            }
        }
    }
    
    
    private static void connectToNeighbours() { 
        
        if (rank % 2 == 0) { 
            connectToNext();
            connectToPrev();
        } else { 
            connectToPrev();
            connectToNext();
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
        System.out.println();        
    } 

    private static void runSender() throws IOException { 
        
        //byte [] header = new byte[headersize];
        byte [] data = new byte[datasize];
               
        long start = System.currentTimeMillis();
                
        for (int i=0;i<count;i++) {
          //  out.write(header);
            out.write(data);
            out.flush();
        }
        
        long end = System.currentTimeMillis();
        
        long time = end-start;
        double tp = ((count*datasize)/(1024.0*1024.0))/(time/1000.0);
        
        System.out.println("Test took " + time + " ms. TP = " + tp + " MB/s.");
    }

    private static void readFully(byte [] buffer) throws IOException { 
        int n = 0;

        do { 
            n += in.read(buffer, n, buffer.length-n);
        } while (n < buffer.length);
    }
    
    private static void runReceiver() throws IOException { 
        
        byte [] header = new byte[headersize];        
        byte [] data = new byte[datasize];
               
        for (int i=0;i<count;i++) {    
           
            if (useHeader) {
                if (useRealloc) { 
                    header = new byte[headersize];
                } 
             
                readFully(header);                
            }
            
            if (useRealloc) { 
                data = new byte[datasize];
            }

            readFully(data);
            
            if (rank != size-1) {
                out.write(data);
                out.flush();
            }
        }
    }

    
    private static void runTPTest() throws IOException { 
        
        for (int i=0;i<repeat;i++) {                                    
            if (rank == 0) { 
                runSender();            
            } else { 
                runReceiver();
            }
        }
    }
    
    public static void parseOptions(String [] args) {
        
        for (int i=0;i<args.length;i++) {
            
            if (args[i].equals("-count")) { 
                count = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-repeat")) {
                repeat = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-datasize")) {
                datasize = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-headersize")) {
                headersize = Integer.parseInt(args[++i]);            
            } else if (args[i].equals("-servername")) {
                servername = args[++i];
            } else if (args[i].equals("-header")) {
                useHeader = true;
            } else if (args[i].equals("-realloc")) {
                useRealloc = true;
            }
        }
        
        if (servername == null) { 
            System.err.println("Usage java test.chain.Client -servername X");
            System.exit(1);
        }
    }
    
    public static void main(String[] args) {

        parseOptions(args);
                
        try {
            System.out.println("Client started");

            factory = VirtualSocketFactory.createSocketFactory();
            server = factory.createServerSocket(0, 50, null);
           
            System.out.println("Created server socket " 
                    + server.getLocalSocketAddress());
                        
            getPoolFromServer(new VirtualSocketAddress(servername));
            
            System.out.println("Connection to neighbours...");
            
            connectToNeighbours();
            
            System.out.println("Done connection to others, waiting for all " +
                    "incoming connections...");
            
            printResults();            
            
            System.out.println("Starting TP test:");
                
            runTPTest();
                       
        } catch (Exception e) {
            System.err.println("Client got exception " + e);
            e.printStackTrace(System.err);
        }
    }

}
