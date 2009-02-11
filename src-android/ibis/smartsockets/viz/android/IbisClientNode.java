package ibis.smartsockets.viz.android;

import ibis.smartsockets.hub.servicelink.ClientInfo;
import android.graphics.Color;

import com.touchgraph.graphlayout.Node;

public class IbisClientNode extends ClientNode {

    public IbisClientNode(ClientInfo info, HubNode hub) {

        super(info.getClientAddress().toString(), hub);

        setType(Node.TYPE_CIRCLE);

        String adr = info.getClientAddress().toString();

        // System.out.println("Adding Ibis " + adr);

        String id = info.getProperty("ibis");

        setMouseOverText(new String[] { "Ibis: " + id, "Loc : " + adr });

        // setBackColor(Color.parseColor("#0080A0"));
        // setNodeBorderInactiveColor(Color.parseColor("#00546A"));
        setBackColor(Color.YELLOW);
        setNodeBorderColor(Color.RED);

        setLabel("I");
    }
}
