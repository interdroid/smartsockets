package ibis.connect.gossipproxy;

public interface ServiceLinkProtocol {
    public final byte MESSAGE          = 1;
    public final byte DISCONNECT       = 2;
    
    public final byte PROXIES          = 3;
    public final byte LOCAL_CLIENTS    = 4;
    public final byte CLIENTS          = 5;
    public final byte DIRECTION        = 6;
    
    public final byte INFO             = 99;
    
}
