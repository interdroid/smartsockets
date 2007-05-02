package ibis.smartsockets.hub.servicelink;

import ibis.smartsockets.hub.connections.MessageForwarderProtocol;

public interface ServiceLinkProtocol extends MessageForwarderProtocol {
    
    // Registration of client property opcodes
    public static final byte REGISTER_PROPERTY = 30;
    public static final byte UPDATE_PROPERTY   = 31;
    public static final byte REMOVE_PROPERTY   = 32;
    
    public static final byte PROPERTY_ACK      = 33;    
    public static final byte PROPERTY_ACCEPTED = 34;
    public static final byte PROPERTY_REJECTED = 35;
    
    // Client info request opcodes
    public static final byte HUBS              = 40;
    public static final byte HUB_FOR_CLIENT    = 41;    
    public static final byte CLIENTS_FOR_HUB   = 42;
    public static final byte ALL_CLIENTS       = 43;
    public static final byte HUB_DETAILS       = 44;    
    public static final byte DIRECTION         = 45; 
    public static final byte INFO_REPLY        = 49;
    
    // Virtual connection error codes (only used in combination with opcode)    
    public static final byte ERROR_NO_CALLBACK        = 1;           
    public static final byte ERROR_PORT_NOT_FOUND     = 2;       
    public static final byte ERROR_CONNECTION_REFUSED = 3;
    public static final byte ERROR_UNKNOWN_HOST       = 4;           
    public static final byte ERROR_ILLEGAL_TARGET     = 5;       
    public static final byte ERROR_SERVER_OVERLOAD    = 6;
}
