package smartsockets.hub.servicelink;

public interface ServiceLinkProtocol {
    public final byte MESSAGE           = 1;
    public final byte DISCONNECT        = 2;
    
    public final byte HUBS              = 10;
    public final byte HUB_FOR_CLIENT    = 11;    
    public final byte CLIENTS_FOR_HUB   = 12;
    public final byte ALL_CLIENTS       = 13;
    public final byte HUB_DETAILS       = 14;
    
    public final byte DIRECTION         = 20;
    
    public final byte REGISTER_PROPERTY = 30;
    public final byte UPDATE_PROPERTY   = 31;
    public final byte REMOVE_PROPERTY   = 32;
        
    public final byte INFO              = 99;
}
