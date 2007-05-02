package ibis.smartsockets.util;

/**
 * Like java.lang.Exception, but with a cause.
 */
public class PropertyUndefinedException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs an <code>PropertyUndefinedException</code> with <code>null</code> as
     * its error detail message.
     */
    public PropertyUndefinedException() {
        super();
    }

    /**
     * Constructs an <code>PropertyUndefinedException</code> with the specified detail
     * message.
     * 
     * @param s
     *            the detail message
     */
    public PropertyUndefinedException(String s) {
        super(s);
    }

    /**
     * Constructs an <code>PropertyUndefinedException</code> with the specified detail
     * message and cause.
     * 
     * @param s
     *            the detail message
     * @param cause
     *            the cause
     */
    public PropertyUndefinedException(String s, Throwable cause) {
        super(s, cause);
    }

    /**
     * Constructs an <code>PropertyUndefinedException</code> with the specified cause.
     * 
     * @param cause
     *            the cause
     */
    public PropertyUndefinedException(Throwable cause) {
        super(cause);
    }

    public String toString() {
        String message = super.getMessage();
        Throwable cause = getCause();
        if (message == null) {
            message = "";
        }
        if (cause != null) {
            message += ": " + cause.getMessage();
        }

        return message;
    }
}
