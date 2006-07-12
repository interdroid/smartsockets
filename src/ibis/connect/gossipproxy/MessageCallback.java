package ibis.connect.gossipproxy;

import ibis.connect.direct.SocketAddressSet;

public interface MessageCallback {
    void gotMessage(SocketAddressSet src, int opcode, String message);        
}
