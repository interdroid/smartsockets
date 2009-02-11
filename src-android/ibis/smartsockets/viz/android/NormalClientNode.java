package ibis.smartsockets.viz.android;

import ibis.smartsockets.hub.servicelink.ClientInfo;

import com.touchgraph.graphlayout.Node;

public class NormalClientNode extends ClientNode {

    // private ClientInfo client;

    public NormalClientNode(ClientInfo info, HubNode hub) {
        super(info.getClientAddress().toString(), hub);

        setType(Node.TYPE_CIRCLE);

        String adr = info.getClientAddress().toString();

        // System.out.println("Adding client " + adr);
        setMouseOverText(new String[] { "Client:", adr });
        setLabel("C");
    }
}
