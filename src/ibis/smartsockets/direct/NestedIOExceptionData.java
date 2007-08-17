package ibis.smartsockets.direct;

public class NestedIOExceptionData {

    public final String description;
    public final Throwable cause;
    
    public NestedIOExceptionData(String description, Throwable cause) { 
        this.description = description;
        this.cause = cause;
    }
}
