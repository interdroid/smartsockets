package ibis.connect.gossipproxy;

public interface ProxyProtocol {

    public static final byte PROXY_CONNECT = 1;       
    public static final byte REPLY_CONNECTION_ACCEPTED = 2;    
    public static final byte REPLY_CONNECTION_REFUSED = 3;
    
    public static final byte PROXY_PING   = 10;
    public static final byte PROXY_GOSSIP = 11;

    public static final byte PROXY_MESSAGE = 20;
            
    public static final byte CLIENT_MESSAGE = 30;
    
    public static final byte PROXY_SERVICELINK_CONNECT  = 40;       
    public static final byte REPLY_SERVICELINK_ACCEPTED = 41;    
    public static final byte REPLY_SERVICELINK_REFUSED  = 42;
   
        
    public static final byte PROXY_CLIENT_REGISTER = 50;       
    public static final byte REPLY_CLIENT_REGISTRATION_ACCEPTED = 51;    
    public static final byte REPLY_CLIENT_REGISTRATION_REFUSED = 52;
   
    public static final byte PROXY_CLIENT_CONNECT = 100;       
    public static final byte REPLY_CLIENT_CONNECTION_ACCEPTED = 101;    
    public static final byte REPLY_CLIENT_CONNECTION_UNKNOWN_HOST = 102;
    public static final byte REPLY_CLIENT_CONNECTION_DENIED = 103;    
    
    
    
} 
