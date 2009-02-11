package ibis.smartsockets.viz.android;

import ibis.smartsockets.hub.servicelink.ClientInfo;

import com.touchgraph.graphlayout.Node;

public class VizClientNode extends ClientNode {

    VizClientNode(ClientInfo info, HubNode hub) {
        super(info.getClientAddress().toString(), hub);

        setType(Node.TYPE_CIRCLE);
        setLabel("V");
    }
}
