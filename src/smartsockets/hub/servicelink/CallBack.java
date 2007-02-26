package smartsockets.hub.servicelink;

import smartsockets.direct.DirectSocketAddress;

public interface CallBack {
    void gotMessage(DirectSocketAddress src, DirectSocketAddress srcProxy, int opcode,
            byte [][] message);        
}
