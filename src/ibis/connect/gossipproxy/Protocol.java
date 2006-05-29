package ibis.connect.gossipproxy;

public interface Protocol {

    public static final byte PROXY_CONNECT = 1;       
    public static final byte REPLY_CONNECTION_ACCEPTED = 2;    
    public static final byte REPLY_CONNECTION_REFUSED = 3;
    
    public static final byte PROXY_PING   = 4;
    public static final byte PROXY_GOSSIP = 5;
    
    
} 
