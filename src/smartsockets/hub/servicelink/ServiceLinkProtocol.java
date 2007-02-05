package smartsockets.hub.servicelink;

public interface ServiceLinkProtocol {
    
    public static final byte DISCONNECT        = 2;
    
    // Client info request opcodes
    public static final byte HUBS              = 10;
    public static final byte HUB_FOR_CLIENT    = 11;    
    public static final byte CLIENTS_FOR_HUB   = 12;
    public static final byte ALL_CLIENTS       = 13;
    public static final byte HUB_DETAILS       = 14;
    
    public static final byte DIRECTION         = 20;
   
    public static final byte INFO              = 99;
    
    // Registration of client property opcodes
    public static final byte REGISTER_PROPERTY = 30;
    public static final byte UPDATE_PROPERTY   = 31;
    public static final byte REMOVE_PROPERTY   = 32;
    
    public static final byte PROPERTY_ACK      = 33;    
    public static final byte PROPERTY_ACCEPTED = 34;
    public static final byte PROPERTY_REJECTED = 35;
    
    // Virtual connection error codes    
    public static final byte ERROR_NO_CALLBACK        = 1;           
    public static final byte ERROR_PORT_NOT_FOUND     = 2;       
    public static final byte ERROR_CONNECTION_REFUSED = 3;
    public static final byte ERROR_UNKNOWN_HOST       = 4;           
    public static final byte ERROR_ILLEGAL_TARGET     = 5;       
      
}
