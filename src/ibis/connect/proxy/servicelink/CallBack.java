package ibis.connect.proxy.servicelink;

import ibis.connect.direct.SocketAddressSet;

public interface CallBack {
    void gotMessage(SocketAddressSet src, SocketAddressSet srcProxy, int opcode,
            String message);        
}
