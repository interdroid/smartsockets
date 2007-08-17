package ibis.smartsockets.virtual;

public class NoModulesDefinedException extends Exception {

    private static final long serialVersionUID = 2176224611541872419L;

    public NoModulesDefinedException(String message) { 
        super(message);
    }
    
    public NoModulesDefinedException(String message, Throwable cause) { 
        super(message, cause);
    }    
}
