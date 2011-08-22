package ibis.smartsockets.hub;

public interface ConnectionProtocol {

    public static final byte HUB_CONNECT         = 1;
    public static final byte SERVICELINK_CONNECT = 2;

    public static final byte CONNECTION_ACCEPTED = 3;
    public static final byte CONNECTION_REFUSED  = 4;
    public static final byte DISCONNECT          = 5;

    public static final byte PING                = 7;
    public static final byte GET_SPLICE_INFO     = 8;

}
