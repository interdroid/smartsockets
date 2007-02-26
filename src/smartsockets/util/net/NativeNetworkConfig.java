package smartsockets.util.net;

/** try to determine MAC address of local network card; this is done
 using a shell to run ifconfig (linux) or ipconfig (windows). The
 output of the processes will be parsed.

 <p>

 To run the whole thing, just type java NetworkInfo

 <p>

 Current restrictions:

 <ul>
 <li>Will probably not run in applets

 <li>Tested Windows / Linux only

 <li>Tested J2SDK 1.4 only

 <li>If a computer has more than one network adapters, only
 one MAC address will be returned

 <li>will not run if user does not have permissions to run
 ifconfig/ipconfig (e.g. under linux this is typically
 only permitted for root)
 </ul>

 */

import ibis.util.RunProcess;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import smartsockets.util.NetworkUtils;

public final class NativeNetworkConfig {
      
    private static List<NetworkInfo> info = new LinkedList<NetworkInfo>();
        
    private static List<NetworkInfoParser> parsers = 
        new LinkedList<NetworkInfoParser>(); 
    
    static { 
        // TODO: use reflection here ?
        parsers.add(new LinuxNetworkInfoParser());
        parsers.add(new WindowsNetworkInfoParser());
        parsers.add(new OSXNetworkInfoParser());
        parsers.add(new SolarisNetworkInfoParser());     
        
        try { 
            getNetworkInfo();
        } catch (Exception e) {
            // TODO: print something ?   
        }
    }
        
    private final static void getNetworkInfo() throws IOException {
        String os = System.getProperty("os.name");

        Iterator itt = parsers.iterator();
        
        while (itt.hasNext()) { 
            NetworkInfoParser parser = (NetworkInfoParser) itt.next();
            
            if (os.startsWith(parser.osName)) {
                
                if (getInfo(parser)) {
                    return;
                }                 
            }
        }
    
        throw new IOException("Unable to retrieve network info for " + os);        
    }
    
    private static boolean getInfo(NetworkInfoParser p) { 
        
        for (int i=0;i<p.numberOfCommands();i++) {
            String [] tmp = p.getCommand(i);
            
            if (getInfo(p, tmp)) { 
                return true;
            }
        }       
        
        return false;
    } 
            
    private static boolean getInfo(NetworkInfoParser p, String [] command) { 
                
        RunProcess rp = new RunProcess(command, new String[0]); 

        byte [] errors = rp.getStderr();
        
        if (errors.length != 0) { 
            
           // System.out.println("Command " + command[0] + " failed");
            
            // Failed to get info!
            return false;
        }
        
        byte [] output = rp.getStdout();
        
        if (output.length == 0) {
            // No ouput ?
            return false;
        }
        
        return p.parse(output, info);        
    }

    public static NetworkInfo getNetworkInfo(InetAddress ip) {         
        
        for (NetworkInfo nw : info) {
            if (ip.equals(nw.ipv4) || ip.equals(nw.ipv6)) { 
                return nw;
            }
        }
        
        return null;        
    }       
   
    public static byte [] getBroadcast(InetAddress ip) throws IOException {
        NetworkInfo nw = getNetworkInfo(ip);                
        
        if (nw == null || nw.broadcast == null) { 
            throw new IOException("Failed to retrieve BROADCAST for " + 
                    NetworkUtils.ipToString(ip));
        }
        
        return nw.broadcast;        
    }

    public static byte[] getMACAddress(InetAddress ip) throws IOException {
        NetworkInfo nw = getNetworkInfo(ip);                
        
        if (nw == null || nw.broadcast == null) { 
            throw new IOException("Failed to retrieve BROADCAST for " + 
                    NetworkUtils.ipToString(ip));
        }
        
        return nw.mac;
    }

    public static byte[] getNetmask(InetAddress ip) throws IOException {
        NetworkInfo nw = getNetworkInfo(ip);                
        
        if (nw == null || nw.broadcast == null) { 
            throw new IOException("Failed to retrieve BROADCAST for " + 
                    NetworkUtils.ipToString(ip));
        }
        
        return nw.netmask;
    }
    
    
}
