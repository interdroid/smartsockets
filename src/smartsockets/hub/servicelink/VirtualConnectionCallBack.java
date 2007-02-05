package smartsockets.hub.servicelink;

import smartsockets.direct.SocketAddressSet;

public interface VirtualConnectionCallBack {

    void connect(SocketAddressSet src, SocketAddressSet sourceHub, int port, 
            int fragment, int buffer, int timeout, long index);
    
    void connectACK(long index, int fragment, int buffer);    
    void connectNACK(long index, byte reason);
    
    void disconnect(long index);
    
    void gotMessage(long index, byte [] data);    
    void gotMessageACK(long index, int data);
}
