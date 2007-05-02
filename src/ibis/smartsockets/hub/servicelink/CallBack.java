package ibis.smartsockets.hub.servicelink;

import ibis.smartsockets.direct.DirectSocketAddress;

public interface CallBack {
    void gotMessage(DirectSocketAddress src, DirectSocketAddress srcProxy, int opcode,
            byte [][] message);        
}
