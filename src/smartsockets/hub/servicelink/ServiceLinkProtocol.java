package smartsockets.hub.servicelink;

public interface ServiceLinkProtocol {
    public final byte MESSAGE           = 1;
    public final byte DISCONNECT        = 2;
    
    public final byte PROXIES           = 10;
    public final byte PROXY_FOR_CLIENT  = 11;    
    public final byte CLIENTS_FOR_PROXY = 12;
    public final byte ALL_CLIENTS       = 13;
    
    public final byte DIRECTION         = 20;
    public final byte REGISTER_SERVICE  = 30;
    
    public final byte INFO             = 99;
}
