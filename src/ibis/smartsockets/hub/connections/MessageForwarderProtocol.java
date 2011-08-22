package ibis.smartsockets.hub.connections;

import ibis.smartsockets.hub.ConnectionProtocol;

public interface MessageForwarderProtocol extends ConnectionProtocol {

    public static final byte CREATE_VIRTUAL          = 60;
    public static final byte CREATE_VIRTUAL_ACK      = 61;
    public static final byte CREATE_VIRTUAL_NACK     = 62;
    public static final byte CREATE_VIRTUAL_ACK_ACK  = 63;

    public static final byte CLOSE_VIRTUAL           = 64;

    public static final byte MESSAGE_VIRTUAL         = 65;
    public static final byte MESSAGE_VIRTUAL_ACK     = 66;

    public static final byte DATA_MESSAGE            = 68;
    public static final byte INFO_MESSAGE            = 69;

}
