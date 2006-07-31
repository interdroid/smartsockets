package ibis.connect.proxy;

public interface ProxyProtocol {

    public static final byte CONNECT = 1;       
    public static final byte CONNECTION_ACCEPTED = 2;    
    public static final byte CONNECTION_REFUSED = 3;
    
    public static final byte PING   = 10;
    public static final byte GOSSIP = 11;
            
    public static final byte CLIENT_MESSAGE = 30;
    
    public static final byte SERVICELINK_CONNECT  = 40;       
    public static final byte SERVICELINK_ACCEPTED = 41;    
    public static final byte SERVICELINK_REFUSED  = 42;
    
    public static final byte BOUNCE_IP = 127;            
} 
