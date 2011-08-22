package ibis.smartsockets.virtual;

public class NonFatalIOException extends Exception {

    private static final long serialVersionUID = 1220215084768722435L;

    public NonFatalIOException(String message) {
        super(message);
    }

    public NonFatalIOException(Throwable cause) {
        super(null, cause);
    }

    public NonFatalIOException(String message, Throwable cause) {
        super(message, cause);
    }

    public String toString() {

        if (getCause() == null) {
            return getClass().getName() + ": " + getLocalizedMessage();
        }

        if (getLocalizedMessage() == null) {
            return getCause().toString();
        }

        return getClass().getName() + ": " + getLocalizedMessage() + " -> "
            + getCause().toString();
    }
}
