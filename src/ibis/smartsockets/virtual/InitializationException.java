package ibis.smartsockets.virtual;

public class InitializationException extends Exception {

    private static final long serialVersionUID = 2144447287143637263L;

    public InitializationException(String message) { 
        super(message);
    }
    
    public InitializationException(String message, Throwable cause) { 
        super(message, cause);
    }    
}
