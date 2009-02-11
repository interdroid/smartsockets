package ibis.smartsockets.viz.android;

import com.touchgraph.graphlayout.Node;

public class CollectionClientNode extends ClientNode {

    public CollectionClientNode(int clients, HubNode hub) {

        super("ClientCollection" + hub.getID(), hub);

        // System.out.println("Adding client client collection: " + clients);

        setType(Node.TYPE_CIRCLE);
        setMouseOverText(new String[] { "Clients :", "" + clients });
        setLabel("" + clients);
    }

    public void setClients(int clients) {
        setMouseOverText(new String[] { "Clients :", "" + clients });
        setLabel("" + clients);
    }
}
