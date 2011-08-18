package ibis.smartsockets.direct;

import java.io.Serializable;

/**
 * This class encapsulates a Throwable and a description (String) of its origin.   
 * 
 * @author Jason Maassen
 * @version 1.0 Dec 19, 2005
 * @since 1.0
 * 
 */
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
