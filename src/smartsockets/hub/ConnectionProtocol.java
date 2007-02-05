package smartsockets.hub;

public interface ConnectionProtocol {

    public static final byte HUB_CONNECT = 1;       
    public static final byte HUB_CONNECTION_ACCEPTED = 2;    
    public static final byte HUB_CONNECTION_REFUSED = 3;
    
    public static final byte PING = 10;
    public static final byte GET_SPLICE_INFO = 50;

    public static final byte SERVICELINK_CONNECT  = 40;       
    public static final byte SERVICELINK_ACCEPTED = 41;    
    public static final byte SERVICELINK_REFUSED  = 42;

}
