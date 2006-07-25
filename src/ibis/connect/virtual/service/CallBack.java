package ibis.connect.virtual.service;

import ibis.connect.direct.SocketAddressSet;

public interface CallBack {
    void gotMessage(SocketAddressSet src, int opcode, String message);        
}
