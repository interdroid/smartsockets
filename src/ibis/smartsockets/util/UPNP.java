package ibis.smartsockets.util;

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Random;
import java.util.StringTokenizer;

import net.sbbi.upnp.Discovery;
import net.sbbi.upnp.devices.UPNPDevice;
import net.sbbi.upnp.devices.UPNPRootDevice;
import net.sbbi.upnp.messages.ActionMessage;
import net.sbbi.upnp.messages.ActionResponse;
import net.sbbi.upnp.messages.UPNPMessageFactory;
import net.sbbi.upnp.messages.UPNPResponseException;
import net.sbbi.upnp.services.UPNPService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
 
public class UPNP {
    
    private static Logger logger = 
        LoggerFactory.getLogger(UPNP.class.getName());
    
    private static final String GATEWAY_DEVICE_URN = 
        "urn:schemas-upnp-org:device:InternetGatewayDevice:1";                    
               
    private static final String WAN_CONNECTION_DEVICE_URN = 
        "urn:schemas-upnp-org:device:WANConnectionDevice:1";
            
    private static final String WAN_CONNECTION_URN = 
        "urn:schemas-upnp-org:service:WANIPConnection:1";
    
    private static final String LAN_DEVICE_URN = 
        "urn:schemas-upnp-org:device:LANDevice:1"; 
    
    private static final String LAN_CONFIG_URN = 
        "urn:schemas-upnp-org:service:LANHostConfigManagement:1";
    
    /*
    private static final String WAN_COMMON_URN = 
        "urn:schemas-upnp-org:service:WANCommonInterfaceConfig:1";
    
    private static final String WAN_DEVICE_URN = 
        "urn:schemas-upnp-org:device:WANDevice:1";
    
    private static final String WAN_LINK_CONFIG_URN = 
        "urn:schemas-upnp-org:service:WANDSLLinkConfig:1";
     */
    
    private static final int MAX_MAPPING_TRIES = 4;
    private static final int DISCOVERY_TIMEOUT = 1500;

    private static UPNPRootDevice root;
    
    
    private static UPNPDevice wanConnection;
    private static UPNPService wanConnectionService;
    
    /*
    private static UPNPDevice wan;    
    private static UPNPService wanCommonConfigService;    
    private static UPNPService wanLinkConfigService;
      */
    
    private static UPNPDevice lan;
    private static UPNPService lanConfigService;
                       
    private static Random random = new Random();
    
    private static boolean getRootDevice() {
        
        if (root == null) {         
            try {        
                UPNPRootDevice[] devices = Discovery.discover(DISCOVERY_TIMEOUT, 
                        GATEWAY_DEVICE_URN);
    
                if (devices != null) {
                    if (logger.isDebugEnabled()) { 
                        logger.debug("Found "+ devices.length + " UPNP device(s).");
                    }

                    if (devices.length > 0) { 
                        root = devices[0];
                    } 
                } else {
                    if (logger.isDebugEnabled()) {
                        logger.debug("No UPNP devices found");
                    }
                }
            } catch (IOException e) {
                if (logger.isDebugEnabled()) {
                    logger.debug("No UPNP devices found");
                }
            }
        }
        
        return (root != null);
    }
  
    private static boolean getWANConnectionDevice() {
        
        if (!getRootDevice()) {
            return false;
        } 
            
        if (wanConnection == null) {                     
            wanConnection = root.getChildDevice(WAN_CONNECTION_DEVICE_URN);
        } 
              
        return (wanConnection != null);
    }

    private static boolean getWANConnectionService() {
        
        if (!getWANConnectionDevice()) {
            return false;
        } 
          
        if (wanConnectionService == null) { 
            wanConnectionService = wanConnection.getService(WAN_CONNECTION_URN);
        } 
        
        return (wanConnectionService != null);
    }
    
    private static boolean getLANDevice() {  
        
        if (!getRootDevice()) {
            return false;
        } 
            
        if (lan == null) {                     
            lan = root.getChildDevice(LAN_DEVICE_URN);
        } 
              
        return (lan != null);
    }
    
    private static boolean getLANConfigService() {
        
        if (!getLANDevice()) {
            return false;
        } 
          
        if (lanConfigService == null) { 
            lanConfigService = lan.getService(LAN_CONFIG_URN);
        } 
        
        return (lanConfigService != null);
    }
    
    private static String simpleStringInvocation(UPNPService service, 
            String name) {  
                
        UPNPMessageFactory factory = 
            UPNPMessageFactory.getNewInstance(service);
        
        ActionMessage action = factory.getMessage("Get" + name); 

        if (action != null) {
            try {
                ActionResponse r = action.service();
                return r.getOutActionArgumentValue("New" + name);                              
            } catch (Exception e) {
                System.out.println("Failed to perform invocation " + e);                
            }
        } else { 
            System.out.println("Action \"Get" + name + "\" not found");
        }
        
        return null;
    }
    
    public static InetAddress getExternalAddress() {        
        if (!getWANConnectionService()) { 
            return null;
        }
        
        String tmp = simpleStringInvocation(wanConnectionService, 
                "ExternalIPAddress");
        
        try { 
            return InetAddressCache.getByName(tmp);
        } catch (UnknownHostException e) {        
            return null;
        }        
    } 
    
        
    public static byte [] getSubnetMask() {        
        if (!getLANConfigService()) { 
            return null;
        }
        
        String tmp = simpleStringInvocation(lanConfigService, "SubnetMask");        
        StringTokenizer tok = new StringTokenizer(tmp, ".");
        
        int tokens = tok.countTokens();
        
        if (!(tokens == 4 || tokens == 16)) {
            // TODO: decent error -> not IPv4 or IPv6
            return null;
        }

        byte [] result = new byte[tok.countTokens()];
        
        for (int i=0;i<tokens;i++) {             
            result[i] = (byte) Integer.parseInt(tok.nextToken());            
        }
        
        return result;        
    } 
    
    public static String getDomainName() {
        if (!getLANConfigService()) { 
            return null;
        }
        return simpleStringInvocation(lanConfigService, "DomainName"); 
    }
              
    public static InetAddress [] getAddressRange() {
        if (!getLANConfigService()) {
            return null;
        }
 
        UPNPMessageFactory factory =
            UPNPMessageFactory.getNewInstance(lanConfigService);

        ActionMessage action = factory.getMessage("GetAddressRange");

        if (action != null) {
            try {
                ActionResponse r = action.service();
                
                String min = r.getOutActionArgumentValue("NewMinAddress");
                String max = r.getOutActionArgumentValue("NewMaxAddress");

                InetAddress [] result = new InetAddress[2];
                
                result[0] = InetAddressCache.getByName(min);
                result[1] = InetAddressCache.getByName(max);
                
                return result;		
            } catch (Exception e) {
                System.out.println("Failed to perform invocation " + e);
            }
        } else {
            System.out.println("Action \"GetAddressRange\" not found");
        }

        return null;
    }
    
   
    
    private static boolean checkProtocol(String protocol) {        
        return protocol != null && (protocol.equalsIgnoreCase("TCP") || 
                protocol.equalsIgnoreCase("UDP"));
    }
      
    private static boolean checkPortRange(int port) { 
        return (port >= 1 || port <= 65535);
    }
              
    private static int performMapping(UPNPMessageFactory factory, 
            int internalPort, int externalPort, String internalClient, 
            int leaseDuration, String protocol) {

        ActionMessage action = factory.getMessage("AddPortMapping");
        
        if (action == null) {
            logger.warn("UPNP action \"AddPortMapping\" not found");
            return 1;
        } 
        
        action.setInputParameter("NewRemoteHost", ""); // wildcard source
        action.setInputParameter("NewExternalPort", externalPort);
        action.setInputParameter("NewInternalPort", internalPort);
        action.setInputParameter("NewInternalClient", internalClient);
        action.setInputParameter("NewProtocol", protocol);            
        action.setInputParameter("NewEnabled", "1");
        action.setInputParameter("NewPortMappingDescription", "SmartNet");
        action.setInputParameter("NewLeaseDuration", leaseDuration);
                        
        try {
            action.service();
            return 0;
        } catch (UPNPResponseException e) {                                
            return e.getDetailErrorCode();
        } catch (Exception e) {
            
            System.err.println("UPNP port fw exeception: " + e);
            e.printStackTrace(System.err);
            
            return 1;
        } 
    } 
           
    private static int getRandomPort() {
        // TODO: should start at a higher value ? 
        return 1 + random.nextInt(65535);
    }
        
    public static int addPortMapping(int internalPort, int externalPort, 
            String internalClient, int leaseDuration, String protocol) 
        throws IOException {

        if (internalClient == null || internalClient.length() == 0) { 
            logger.warn("Internal client not defined: " + internalClient);            
            throw new IllegalArgumentException("Internal client not defined: " 
                    + internalClient);
        }
        
        if (externalPort != 0 && !checkPortRange(externalPort)) {
            logger.warn("External port out of range: " + externalPort);            
            throw new IllegalArgumentException("External port out of range: " 
                    + externalPort);
        }
        
        if (!checkPortRange(internalPort)) { 
            logger.warn("Internal port out of range: " + internalPort);
            throw new IllegalArgumentException("Internal port out of range: " 
                    + internalPort);
        }
        
        if (!checkProtocol(protocol)) {
            logger.warn("Unsupported protocol: " + protocol);
            throw new IllegalArgumentException("Unsupported protocol");
        }
                        
        if (leaseDuration < 0) { 
            logger.warn("Invalid lease duration: " + leaseDuration);
            throw new IllegalArgumentException("Invalid lease duration: " 
                    + leaseDuration);
        }

        if (!getWANConnectionDevice()) {
            logger.warn("Failed to contact WAN device");
            throw new IOException("Failed to connect to WAN device");
        }

        boolean anyExternal = false;
        
        if (externalPort == 0) { 
            // Use any available external port. We start by trying the same 
            // value as internalPort. 
            anyExternal = true;
            externalPort = internalPort;            
        }
        
        UPNPMessageFactory factory =
            UPNPMessageFactory.getNewInstance(wanConnectionService);

        if (factory == null) { 
        	throw new IOException("Failed to create UPNPMessageFactory");
        }
        
        int loop = 0;
        
        while (true) {

            if (loop++ > MAX_MAPPING_TRIES) { 
                logger.warn("Port mapping did not succeed in " 
                        + MAX_MAPPING_TRIES + " tries.");                
                throw new IOException("Port mapping did not succeed in " 
                        + MAX_MAPPING_TRIES + " tries."); 
            }

            int result = performMapping(factory, internalPort, externalPort, 
                internalClient, leaseDuration, protocol);
            
            /* Possible error codes returned by performMapping:
             * 
             * 0 Mapping OK (my code)
             * 1 Mapping failed (my code)
             * 
             * 402 Invalid Args (General UPNP error)
             * 501 Action Failed (General UPNP error)
             * 
             * 715 Wildcard is not allowed in source IP 
             * 716 Wildcard in not allowed in external port
             * 718 External port is already in use
             * 724 Internal and external port values must be the same
             * 725 The NAT only supports permanent lease times
             * 726 RemoteHost must be a wildcard
             * 727 ExternalPort must be a wildcard
             */
            switch (result) { 
            case 0:
                // Success
                if (logger.isDebugEnabled()) { 
                    logger.debug("Port forwarding activated from external port "
                            + externalPort + " to " + internalClient + ":" 
                            + internalPort);
                }
                
                return externalPort;
            
            case 718: 
                if (!anyExternal) {  
                    // We are not allowed to try other external port values, so 
                    // the call fails
                    throw new BindException("External port already in use");
                } else { 
                    // Port is already in use, but we are allowed to try other 
                    // ports, so pick a random one and try again.
                    externalPort = getRandomPort();
                }
                break;

            case 724:
                // This can only happen it we started with externalPort == 0, 
                // set it to internalPort, and then got a 718 error. We then 
                // tried some random other external port, but that is not 
                // supported by the NAT box. Hence this error.
                throw new BindException("NAT box can only use port " 
                        + internalClient + " which is already in use");
                
            case 725:
                if (leaseDuration == 0) {
                    // The NAT box gave a strange reply. Assume it failed.
                    logger.warn("Got unexpected reply from NAT box");
                    throw new IOException("Got unexpected reply from NAT box");
                }  
                        
                // The NAT box can only handle infinite leases, so 'upgrade' 
                // the users request. Should always be safe to do ?  
                leaseDuration = 0;
                break;
               
            default: 
                // In all other cases (1, 501, 402, 715, 716, 726, 727) we fail. 
                // Note that 716 and 726 should never occur, since our external
                // port is never a wildcard, while our remote host is always a 
                // wildcard.                
                logger.warn("Port forwarding failed with error: " + result);
                throw new IOException("Port forwarding failed with error: " 
                        + result); 
            }
        } 
    }    
    
    public static boolean deletePortMapping(int externalPort, String protocol) { 

        if (!checkPortRange(externalPort)) { 
            logger.warn("External port out of range: " + externalPort);
            throw new IllegalArgumentException("External port out of range: " 
                    + externalPort);
        }
                
        if (!checkProtocol(protocol)) { 
            logger.warn("Unsupported protocol: " + protocol);
            throw new IllegalArgumentException("Unsupported protocol: " 
                    + protocol);
        }
                
        if (!getWANConnectionDevice()) {
            logger.warn("Failed to contact WAN device");
            return false;
        }
        
        UPNPMessageFactory factory =
            UPNPMessageFactory.getNewInstance(wanConnectionService);

        ActionMessage action = factory.getMessage("DeletePortMapping");
                
        if (action != null) {
            action.setInputParameter("NewRemoteHost", ""); // wildcard source
            action.setInputParameter("NewExternalPort", externalPort);
            action.setInputParameter("NewProtocol", protocol);            
                        
            try {
                action.service();
                return true;
            } catch (Exception e) {
                logger.warn("Failed to perform UPNP invocation " + e);
                System.out.println("Failed to perform UPNP invocation " + e);                
            }
        } else {
            logger.warn("UPNP action \"DeletePortMapping\" not found");
            System.out.println("UPNP action \"DeletePortMapping\" not found");
        }

        return false;
    }
    
    /* The rest is unused at the moment....    
     private static boolean getWANDevice() {
        
          if (!getRootDevice()) {
              return false;
          } 
            
          if (wan == null) {                     
              wan = root.getChildDevice(WAN_DEVICE_URN);
          } 
              
          return (wan != null);
      }
 
      private static boolean getWANCommonConfigService() {
        
        if (!getWANDevice()) {
            return false;
        } 
          
        if (wanCommonConfigService == null) { 
            wanCommonConfigService = wan.getService(WAN_COMMON_URN);
        } 
        
        return (wanCommonConfigService != null);
    }
    
    private static boolean getWANLinkConfigService() {
        
        if (!getWANConnectionDevice()) {
            return false;
        } 
          
        if (wanLinkConfigService == null) { 
            wanLinkConfigService = wanConnection.getService(WAN_LINK_CONFIG_URN);
        } 
        
        return (wanLinkConfigService != null);
    }
    
    private static String getDestinationAddress() {        
        if (!getWANLinkConfigService()) {
            System.out.println("Failed to get link config service");
            return null;
        }
        return simpleStringInvocation(wanLinkConfigService, 
                "DestinationAddress"); 
    } 
   
    private static String getAccessProvider() {
        if (!getWANCommonConfigService()) { 
            return null;
        }
        return simpleStringInvocation(wanCommonConfigService, 
                "WANAccessProvider"); 
    } 
    
    private static String getTotalPacketsReceived() {
        if (!getWANCommonConfigService()) { 
            return null;
        }
        return simpleStringInvocation(wanCommonConfigService, 
                "TotalPacketsReceived"); 
    } 

    private static String getTotalPacketsSent() {
        if (!getWANCommonConfigService()) { 
            return null;
        }
        return simpleStringInvocation(wanCommonConfigService, 
                "TotalPacketsSent"); 
    } 
    
    private static String getTotalBytesReceived() {
        if (!getWANCommonConfigService()) { 
            return null;
        }
        return simpleStringInvocation(wanCommonConfigService, 
                "TotalBytesReceived"); 
    } 

    private static String getTotalBytesSent() {
        if (!getWANCommonConfigService()) { 
            return null;
        }
        return simpleStringInvocation(wanCommonConfigService, 
                "TotalBytesSent"); 
    } 

    private static String getDNSServers() {
        if (!getLANConfigService()) { 
            return null;
        }
        return simpleStringInvocation(lanConfigService, "DNSServers"); 
    }

    private static String getReservedAddress() {
        if (!getLANConfigService()) { 
            return null;
        }
        return simpleStringInvocation(lanConfigService, "ReservedAddresses"); 
    }
*/
}
