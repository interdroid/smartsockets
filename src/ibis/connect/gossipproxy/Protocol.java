package ibis.connect.gossipproxy;

public interface Protocol {

    public static final byte PROXY_CONNECT = 1;       
    public static final byte CONNECTION_ACCEPTED  = 2;    
    public static final byte CONNECTION_DUPLICATE = 3;
    
    public static final byte PROXY_PING = 4;
    
} 
