package test.gossipproxy;




import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedList;

import smartsockets.direct.DirectServerSocket;
import smartsockets.direct.DirectSocketFactory;
import smartsockets.direct.SocketAddressSet;
import smartsockets.hub.servicelink.CallBack;
import smartsockets.hub.servicelink.ClientInfo;
import smartsockets.hub.servicelink.ServiceLink;

public class TestServiceLink implements CallBack {
    
    private static final int DEFAULT_PORT = 14567;
    
    private DirectSocketFactory factory;
    private DirectServerSocket ss; 
    
    private ServiceLink serviceLink; 
    private final boolean interactive;    

    private int message = 0;
    
    private TestServiceLink(int port, LinkedList proxies, boolean interactive) 
        throws IOException {         
    
        this.interactive = interactive;
        
        factory = DirectSocketFactory.getSocketFactory();        
        ss = factory.createServerSocket(port, 10, null);        
        
        while (proxies.size() > 0) {
            serviceLink = ServiceLink.getServiceLink(
                    (SocketAddressSet) proxies.removeFirst(), 
                    ss.getAddressSet());
            
            if (serviceLink != null) { 
                break;
            }
        }
        
        if (serviceLink == null) { 
            System.err.println("Failed to connect ot any proxy!UnknownHostException");
            System.exit(1);
        }
        
        serviceLink.register("TEST", this);
    }
    
    public void gotMessage(SocketAddressSet src, SocketAddressSet srcProxy, 
            int opcode, String message) {
    
        System.out.println("Got message from: " + src + "@" + srcProxy 
                + "\n   [" + opcode + "] - " + message);            
        
        if (!interactive) {
            // bounce the message back to the sender.
            serviceLink.send(src, srcProxy, "TEST", opcode, "I got: " + message);
        }
    }    
    
    private void send(String line) { 
        
        if (line == null && line.length() == 0) {
            System.out.println("Nothing to send!");
            return;
        } 
        
        int index = line.indexOf(' ');
        
        if (index == -1) { 
            System.out.println("Need <target[@proxy]> <txt> as input!");
            return;
        }
        
        String proxy = null;
        String target = line.substring(0, index);
        String txt = line.substring(index).trim();
        
        index = target.indexOf('@');
        
        if (index > 0) { 
            proxy = target.substring(index+1);
            target = target.substring(0, index);
        }
                
        try { 
            SocketAddressSet t = SocketAddressSet.getByAddress(target);
            SocketAddressSet p = null; 
            
            if (proxy != null) { 
                p = SocketAddressSet.getByAddress(proxy);
            }

            serviceLink.send(t, p, "TEST", message++, txt);
        } catch (Exception e) {
            System.out.println("Failed to parse target address!" + e);
        }           
    }
    
    private void proxies() { 
        
        try {
            SocketAddressSet [] result = serviceLink.hubs();
        
            System.out.println("Known proxies:" + result.length);
            
            for (int i=0;i<result.length;i++) { 
                System.out.println(i + ": " + result[i]);
            }
                
        } catch (IOException e) {
            System.out.println("Failed to retrieve proxy list!" + e);
        }
    }

    private void allClients() { 
        
        try {
            ClientInfo [] result = serviceLink.clients();
        
            System.out.println("Known clients:" + result.length);
            
            for (int i=0;i<result.length;i++) { 
                System.out.println(i + ": " + result[i]);
            }
                
        } catch (Exception e) {
            System.out.println("Failed to retrieve client list!" + e);
        }
    }

    private void localClients() { 
        
        try {
            ClientInfo [] result = serviceLink.localClients();
        
            System.out.println("Clients sharing proxy:" + result.length);
            
            for (int i=0;i<result.length;i++) { 
                System.out.println(i + ": " + result[i]);
            }
                
        } catch (Exception e) {
            System.out.println("Failed to retrieve local client list!" + e);
        }
    }
    
    private void usage() {         
        System.out.println("help                - this help");
        System.out.println("send <target> <txt> - send text <txt> to <target>");        
        System.out.println("exit                - exit");        
    }
    
    private void parseInput() { 
        
        boolean done = false;
                        
        BufferedReader clin = new BufferedReader(new InputStreamReader(System.in));
        
        try {         
            while (!done) { 
                System.out.print("> ");
                System.out.flush(); 
                
                String line = clin.readLine().trim();
                
                if (line.length() == 0) { 
                    // ignore empty lines....
                } else if (line.startsWith("help")) {
                    usage();
                } else if (line.startsWith("send ")) {
                    send(line.substring(5).trim());
                } else if (line.startsWith("proxies")) {
                    proxies();
                } else if (line.startsWith("clients")) {
                    allClients();                
                } else if (line.startsWith("local clients")) {
                    localClients();                                
                } else if (line.startsWith("exit")) {                    
                    done = true;
                } else {
                    System.out.println("Unknown command, try help");
                }
            }
        } catch (Exception e) {
            System.out.println("Got exception! " + e);
        } 
    }
        
    private void waitForExit() { 
        while (true) { 
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            System.out.println("I'm bored....");            
        }
    }
    
    private void start() { 
        
        if (!interactive) {
            waitForExit();
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
                    proxies.add(SocketAddressSet.getByAddress(args[++i]));
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
            new TestServiceLink(port, proxies, interactive).start();
        } catch (Exception e) { 
            System.err.println("EEK " + e);
            System.exit(1);
        }
    }

    
}
