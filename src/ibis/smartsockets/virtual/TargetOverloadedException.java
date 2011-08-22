package ibis.smartsockets.virtual;

import java.io.IOException;

public class TargetOverloadedException extends IOException {

    private static final long serialVersionUID = -7359497869340017279L;

    public TargetOverloadedException() {
        super();
    }

    public TargetOverloadedException(String message) {
        super(message);
    }

}
