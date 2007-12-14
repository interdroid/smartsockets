package ibis.smartsockets.util;

public class MalformedAddressException extends RuntimeException {

    /** 
     * Generated
     */
    private static final long serialVersionUID = 3651250662722497089L;

    /**
     * Constructs an <code>MalformedAddressException</code> with <code>null</code> as
     * its error detail message.
     */
    public MalformedAddressException() {
        super();
    }

    /**
     * Constructs an <code>MalformedAddressException</code> with the specified detail
     * message.
     * 
     * @param s
     *            the detail message
     */
    public MalformedAddressException(String s) {
        super(s);
    }

    /**
     * Constructs an <code>MalformedAddressException</code> with the specified detail
     * message and cause.
     * 
     * @param s
     *            the detail message
     * @param cause
     *            the cause
     */
    public MalformedAddressException(String s, Throwable cause) {
        super(s, cause);
    }

    /**
     * Constructs an <code>MalformedAddressException</code> with the specified cause.
     * 
     * @param cause
     *            the cause
     */
    public MalformedAddressException(Throwable cause) {
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
