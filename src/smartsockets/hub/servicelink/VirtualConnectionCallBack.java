package smartsockets.hub.servicelink;

import smartsockets.direct.SocketAddressSet;

public interface VirtualConnectionCallBack {

    boolean connect(SocketAddressSet src, String info, int timeout, int vc, String replyID);    
    void disconnect(int vc);
    void gotMessage(int vc, byte [] data);
}
