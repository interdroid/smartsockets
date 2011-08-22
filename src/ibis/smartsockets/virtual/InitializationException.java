package ibis.smartsockets.virtual;

public class InitializationException extends Exception {

    private static final long serialVersionUID = 2144447287143637263L;

    public InitializationException(final String message) {
        super(message);
    }

    public InitializationException(final String message,
            final Throwable cause) {

        super(message, cause);
    }
}
