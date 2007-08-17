package ibis.smartsockets.virtual;

import java.io.IOException;
import java.util.LinkedList;

public class NoSuitableModuleException extends IOException {

    private static final long serialVersionUID = 7906462081071080010L;

    private final String [] names;
    private final Throwable [] causes;
    
    private final LinkedList<NoSuitableModuleException> exceptions;
    
    public NoSuitableModuleException(String message, String [] names, 
            Throwable [] causes) { 
        
        super(message);
        this.names = names;
        this.causes = causes;
        this.exceptions = null;
    }    
    
    public NoSuitableModuleException(String message, 
            LinkedList<NoSuitableModuleException> exceptions) { 
        
        super(message);
        this.names = null;
        this.causes = null;
        this.exceptions = exceptions;
    }    
        
    public String toString() {        
        String s = getClass().getName();
        String message = getLocalizedMessage();
        
        StringBuilder builder = new StringBuilder();

        builder.append(s);
        builder.append(": ");
        
        if (message != null) { 
            builder.append(message);
        } 
        
        builder.append("\n");
        
        if (exceptions == null) { 

            int attempt = 0;
            
            for (NoSuitableModuleException e : exceptions) { 

                builder.append(" Attempt: ");
                builder.append(attempt++);
                builder.append("\n");
                
                builder.append("  " + e.toString());                
                builder.append("\n");
            }        
        } else {
            for (int i=0;i<causes.length;i++) { 

                builder.append("   ");
                builder.append(names[i]);
                builder.append(": ");

                if (causes[i] != null) { 
                    builder.append(causes[i].toString());
                } else { 
                    builder.append("<unknown cause>");
                }

                builder.append("\n");
            }
        }
        
        return builder.toString();
    }
}
