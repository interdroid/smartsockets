package smartsockets.hub.servicelink;

import smartsockets.direct.SocketAddressSet;

public interface CallBack {
    void gotMessage(SocketAddressSet src, SocketAddressSet srcProxy, int opcode,
            String message);        
}