package smartsockets.direct;

import java.io.IOException;

public class FirewallException extends IOException {

    private static final long serialVersionUID = 2195719116622936489L;

    public FirewallException(String message) { 
        super(message);
    }
}
