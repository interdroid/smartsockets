package smartsockets.hub.servicelink;

import smartsockets.direct.SocketAddressSet;

public interface VirtualConnectionCallBack {

    boolean connect(SocketAddressSet src, String info, int timeout, long index);   
    void disconnect(long index);
    void gotMessage(long index, byte [] data);
}
