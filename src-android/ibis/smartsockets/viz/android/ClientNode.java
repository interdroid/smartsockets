package ibis.smartsockets.viz.android;

import ibis.smartsockets.hub.servicelink.ClientInfo;

import java.util.StringTokenizer;

import android.graphics.Color;

import com.touchgraph.graphlayout.Edge;
import com.touchgraph.graphlayout.Node;

public class ClientNode extends SmartNode {

    protected Edge edge;

    protected HubNode hub;

    public ClientNode(String id, HubNode hub) {

        super(id);

        this.hub = hub;
        edge = new Edge(this, hub, 20);
        edge.useArrowHead(false);

    }

    public ClientNode(ClientInfo info, HubNode hub) {

        this(info.getClientAddress().toString(), hub);

        setType(Node.TYPE_CIRCLE);

        update(info, hub);

    }

    public void update(ClientInfo info, HubNode hub) {

        String adr = info.getClientAddress().toString();

        // System.out.println("Adding client " + adr);

        String tmp = info.getProperty("smartsockets.viz");

        String label = getElement(tmp, 0, "C");
        String[] popup = getElements(tmp, 1, new String[] { "Client:", adr });
        String color = getElement(tmp, 2, null);

        setLabel(label);

        if (color != null) {
            setPattern(Color.parseColor(color));
        } else {
            setPattern(hub.getPatern());
        }

        setMouseOverText(popup);
    }

    private String getElement(String s, int num, String def) {

        if (s == null) {
            return def;
        }

        String[] tmp = split(s, "^");

        // System.out.println("tmp = " + tmp + " " + tmp.length);

        if (tmp.length <= num || tmp[num] == null) {
            return def;
        }

        return tmp[num];
    }

    private String[] split(String s, String seperator) {

        StringTokenizer t = new StringTokenizer(s, seperator);

        String[] result = new String[t.countTokens()];

        for (int i = 0; i < result.length; i++) {
            result[i] = t.nextToken();
        }

        return result;
    }

    private String[] getElements(String s, int num, String[] def) {

        String tmp = getElement(s, num, null);

        if (tmp == null) {
            return def;
        }

        return split(tmp, ",");
    }

    public Edge getEdge() {
        return edge;
    }
}
