package ibis.smartsockets.direct;

import java.io.Serializable;

public class NestedIOExceptionData implements Serializable {

    /** 
     * Generated
     */
    private static final long serialVersionUID = 6049338700587184766L;

    public final String description;
    public final Throwable cause;
    
    public NestedIOExceptionData(String description, Throwable cause) { 
        this.description = description;
        this.cause = cause;
    }
}
