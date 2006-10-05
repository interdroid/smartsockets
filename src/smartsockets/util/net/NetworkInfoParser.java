package smartsockets.util.net;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.regex.Pattern;

public abstract class NetworkInfoParser {
    
    protected final static Pattern macPattern = 
        Pattern.compile("([0-9a-fA-F]{2}[\\-:]){5}[0-9a-fA-F]{2}");
    
    protected final static Pattern ipv4Pattern =
        Pattern.compile("\\A(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}\\z");
    
    protected final static Pattern ipv6Pattern =         
        Pattern.compile("\\A(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}\\z");
    
    protected final static Pattern ipv6PatternHexCompressed =         
        Pattern.compile("\\A((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)::((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)\\z");

    protected final static Pattern ipv6Pattern6Hex4Dec =         
        Pattern.compile("\\A((?:[0-9A-Fa-f]{1,4}:){6,6})(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}\\z");

    protected final static Pattern ipv6Pattern6Hex4DecCompressed =         
        Pattern.compile("\\A((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)::((?:[0-9A-Fa-f]{1,4}:)*)(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}\\z");
      
    protected final static boolean isMacAddress(String mac) {
        return macPattern.matcher(mac).matches();        
    }
    
    protected static boolean isIPv4Address(String ip) {
        return ipv4Pattern.matcher(ip).matches(); 
    }
    
    protected static boolean isIPv6Address(String ip) {
        return ipv6Pattern.matcher(ip).matches() || 
            ipv6Pattern6Hex4Dec.matcher(ip).matches() || 
            ipv6Pattern6Hex4DecCompressed.matcher(ip).matches() || 
            ipv6PatternHexCompressed.matcher(ip).matches();
    }
        
    protected static final String getIPv4Field(String line, String header) { 
        int index = line.indexOf(header); 
        
        if (index >= 0) { 
            String tmp = line.substring(index + header.length()).trim();
            
            index = tmp.indexOf(' ');
            
            if (index >= 0) { 
                tmp = tmp.substring(0, index).trim();
            }
             
            if (isIPv4Address(tmp)) {
                return tmp;
            }
        } 
        
        return null;    
    }
    
    protected static final String getIPv6Field(String line, String header) { 
        int index = line.indexOf(header); 
        
        if (index >= 0) { 
            String tmp = line.substring(index + header.length()).trim();
            
            index = tmp.indexOf(' ');
            
            if (index > 0) { 
                tmp = tmp.substring(0, index).trim();
            } 
              
            index = tmp.indexOf('/');
                
            if (index > 0) {
                if (isIPv6Address(tmp.substring(0, index).trim())) {
                    return tmp;
                }
            } else { 
                if (isIPv6Address(tmp)) {
                    return tmp;
                }
            }
        } 
        
        return null;    
    }

    
    
    protected static final byte [] ipStringToBytes(String ip) { 
        try  { 
            return InetAddress.getByName(ip).getAddress();
        } catch (Exception e) {
            // print ??
            return null;
        }
    }
    
    public final String osName;
    
    protected NetworkInfoParser(String osName) {
        this.osName = osName;
    }
    
    abstract int numberOfCommands(); 

    abstract String [] getCommand(int number);
    
    abstract boolean parse(byte [] output, List info);
}
