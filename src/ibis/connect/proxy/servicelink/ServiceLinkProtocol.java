package ibis.connect.proxy.servicelink;

public interface ServiceLinkProtocol {
    public final byte MESSAGE           = 1;
    public final byte DISCONNECT        = 2;
    
    public final byte PROXIES           = 3;
    public final byte CLIENTS_FOR_PROXY = 4;
    public final byte ALL_CLIENTS       = 5;    
    public final byte DIRECTION         = 6;
    public final byte REGISTER_SERVICE  = 7;
    
    public final byte INFO             = 99;
    
    
}