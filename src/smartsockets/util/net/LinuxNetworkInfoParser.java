package smartsockets.util.net;


import java.net.InetAddress;
import java.util.List;
import java.util.StringTokenizer;

import smartsockets.util.NetworkUtils;
import smartsockets.util.net.NetworkInfo;

public class LinuxNetworkInfoParser extends NetworkInfoParser {
    
    private static final String [][] commands = new String [][] { 
        new String [] { "ifconfig" }, 
        new String [] { "/sbin/ifconfig" }, 
        new String [] { "/bin/ifconfig" }         
    };
    
    public LinuxNetworkInfoParser() { 
        super("Linux");
    }
 
    public boolean parse(byte [] output, List info) { 
        
        boolean result = false;
        StringBuffer tmp = new StringBuffer(new String(output));
        
        int start = 0;
        
        for (int i=1;i<tmp.length();i++) {             
            if (tmp.charAt(i) == '\n' && tmp.charAt(i-1) == '\n') { 
                result = parseBlock(tmp.substring(start, i), info) || result;
                start = i;
            }
        }
        
        if (start < tmp.length()-1) { 
            result = parseBlock(tmp.substring(start, tmp.length()), info) || result;            
        }
        
        return result;
    }
     
    private boolean parseBlock(String tmp, List info) { 
               
        // // System.out.println("Parse block:\n" + tmp + "\n\n");
        
        StringTokenizer tokenizer = new StringTokenizer(tmp, "\n");

        NetworkInfo nw = new NetworkInfo();
                
        while (tokenizer.hasMoreTokens()) {
            
            String line = tokenizer.nextToken().trim();
            
            // // System.out.println("Line: " + line);
                               
            int index = line.indexOf("HWaddr "); 
                
            if (index > 0) { 
                String mac = line.substring(index + 6).trim();
                    
                if (isMacAddress(mac)) {
                    // // System.out.println("Got mac " + mac);                    
                    nw.mac = NetworkUtils.MACStringToBytes(mac);
                }           
            }
            
            String t = getIPv4Field(line, "inet addr:");
            
            if (t != null) {
                // .println("Got ipv4 " + t);
                try { 
                    nw.ipv4 = InetAddress.getByName(t);
                } catch (Exception e) {
                    // TODO: handle exception
                }
            }
            
            t = getIPv4Field(line, "Bcast:");
            
            if (t != null) {
                // System.out.println("Got bcast " + t);                                                    
                nw.broadcast = ipStringToBytes(t);     
            }            
             
            t = getIPv4Field(line, "Mask:");
            
            if (t != null) { 
                // System.out.println("Got mask " + t);                                                   
                nw.netmask = ipStringToBytes(t);
            }
            
            t = getIPv6Field(line, "inet6 addr:");
            
            if (t != null) {
                // System.out.println("Got ipv6 " + t);
                try { 
                    nw.ipv6 = InetAddress.getByName(t);
                } catch (Exception e) {
                    // TODO: handle exception
                }
            }
        }
        
        info.add(nw);
                
        return true;
    }

    String[] getCommand(int number) {
        return commands[number];
    }

    int numberOfCommands() {
        return commands.length;
    }
}
