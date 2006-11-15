package smartsockets.hub.servicelink;

public interface ServiceLinkProtocol {
    public static final byte MESSAGE           = 1;
    public static final byte DISCONNECT        = 2;
    
    public static final byte HUBS              = 10;
    public static final byte HUB_FOR_CLIENT    = 11;    
    public static final byte CLIENTS_FOR_HUB   = 12;
    public static final byte ALL_CLIENTS       = 13;
    public static final byte HUB_DETAILS       = 14;
    
    public static final byte DIRECTION         = 20;
    
    public static final byte REGISTER_PROPERTY = 30;
    public static final byte UPDATE_PROPERTY   = 31;
    public static final byte REMOVE_PROPERTY   = 32;
            
    public static final byte CREATE_VIRTUAL      = 60;    
    public static final byte CLOSE_VIRTUAL       = 61;
    public static final byte MESSAGE_VIRTUAL     = 62;
    public static final byte MESSAGE_VIRTUAL_ACK = 63;
    public static final byte REPLY_VIRTUAL       = 64;       
    
    public final byte INFO              = 99;
}
