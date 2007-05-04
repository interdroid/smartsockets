package ibis.smartsockets.virtual.modules;

import ibis.smartsockets.direct.DirectSocket;

public interface AcceptHandler {
    void accept(DirectSocket d, int targetPort);
}
