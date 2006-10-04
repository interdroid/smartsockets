package ibis.connect.virtual.service;

import ibis.connect.direct.SocketAddressSet;

public interface CallBack {
    void gotMessage(SocketAddressSet src, SocketAddressSet srcProxy, int opcode,
            String message);        
}
