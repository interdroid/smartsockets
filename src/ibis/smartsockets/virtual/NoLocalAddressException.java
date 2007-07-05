package ibis.smartsockets.virtual;

public class NoLocalAddressException extends Exception {

    private static final long serialVersionUID = -9096168522774079015L;

    public NoLocalAddressException(String message) { 
        super(message);
    }
    
    public NoLocalAddressException(String message, Throwable cause) { 
        super(message, cause);
    }    
}
