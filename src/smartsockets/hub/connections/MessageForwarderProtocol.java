package smartsockets.hub.connections;

public interface MessageForwarderProtocol {

    public static final byte CLIENT_MESSAGE          = 30;
        
    public static final byte CREATE_VIRTUAL          = 60;    
    public static final byte CREATE_VIRTUAL_ACK      = 61;       
    public static final byte CREATE_VIRTUAL_NACK     = 62;       
    public static final byte CREATE_VIRTUAL_ACK_ACK  = 63;       
        
    public static final byte CLOSE_VIRTUAL           = 64;
    
    public static final byte MESSAGE_VIRTUAL         = 65;
    public static final byte MESSAGE_VIRTUAL_ACK     = 66;
}
