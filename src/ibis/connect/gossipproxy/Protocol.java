package ibis.connect.gossipproxy;

public interface Protocol {

    public static final byte PROXY_CONNECT = 1;       
    public static final byte REPLY_CONNECTION_ACCEPTED = 2;    
    public static final byte REPLY_CONNECTION_REFUSED = 3;
    
    public static final byte PROXY_PING   = 4;
    public static final byte PROXY_GOSSIP = 5;

    public static final byte PROXY_CLIENT_REGISTER = 6;       
    public static final byte REPLY_CLIENT_REGISTRATION_ACCEPTED = 7;    
    public static final byte REPLY_CLIENT_REGISTRATION_REFUSED = 8;
   
    public static final byte PROXY_CLIENT_CONNECT = 9;       
    public static final byte REPLY_CLIENT_CONNECTION_ACCEPTED = 10;    
    public static final byte REPLY_CLIENT_CONNECTION_REFUSED = 11;  
} 
