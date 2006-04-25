package ibis.connect.virtual;

public class ModuleNotSuitableException extends Exception {

    private static final long serialVersionUID = 1220215084768722435L;

    public ModuleNotSuitableException(String message) { 
        super(message);
    }
    
    public ModuleNotSuitableException(String message, Throwable cause) { 
        super(message, cause);
    }    
}
