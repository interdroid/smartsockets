package ibis.smartsockets.virtual;

public class NoSuitableModuleException extends Exception {

    private static final long serialVersionUID = 7906462081071080010L;

    public NoSuitableModuleException(String message) { 
        super(message);
    }
    
    public NoSuitableModuleException(String message, Throwable cause) { 
        super(message, cause);
    }    
}
