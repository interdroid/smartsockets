package ibis.smartsockets.hub.servicelink;

import ibis.smartsockets.direct.DirectSocketAddress;

import java.io.DataInputStream;
import java.io.IOException;


public interface VirtualConnectionCallBack {

    void connect(DirectSocketAddress src, DirectSocketAddress sourceHub, int port,
            int fragment, int buffer, int timeout, long index);

    void connectACK(long index, int fragment, int buffer);
    void connectNACK(long index, byte reason);
    void connectACKACK(long index, boolean succes);

    void disconnect(long index);

    boolean gotMessage(long index, int len, DataInputStream in) throws IOException ;
    void gotMessageACK(long index, int data);

}
