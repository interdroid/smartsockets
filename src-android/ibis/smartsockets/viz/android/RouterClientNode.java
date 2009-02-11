package ibis.smartsockets.viz.android;

import ibis.smartsockets.direct.DirectSocketAddress;
import ibis.smartsockets.hub.servicelink.ClientInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.StringTokenizer;

import android.graphics.Color;

import com.touchgraph.graphlayout.Edge;
import com.touchgraph.graphlayout.Node;

public class RouterClientNode extends ClientNode {

    // private ClientInfo client;

    private ArrayList<String> mouseOverText = new ArrayList<String>();

    private HashMap<String, ConnectionInfo> cons = new HashMap<String, ConnectionInfo>();

    private HashMap<String, ConnectionInfo> oldCons = new HashMap<String, ConnectionInfo>();

    private static class ConnectionInfo {
        String id;

        DirectSocketAddress from;

        DirectSocketAddress to;

        long tp;

        Edge edge1;

        Edge edge2;

        ConnectionInfo(DirectSocketAddress from, DirectSocketAddress to,
                String id, long tp) {
            this.from = from;
            this.to = to;
            this.id = id;
            this.tp = tp;
        }

        String getID() {
            return id;
        }

        long getTP() {
            return tp;
        }
    }

    public RouterClientNode(ClientInfo info, HubNode hub) {
        super(info.getClientAddress().toString(), hub);

        String adr = info.getClientAddress().toString();

        // System.out.println("Adding router " + adr);

        setType(Node.TYPE_CIRCLE);
        setBackColor(Color.parseColor("#FF7F24"));
        setNodeBorderColor(Color.parseColor("#CD661D"));
        setLabel("R");

        update(info);
    }

    private String convert(long tp) {

        if (tp < 1024) {
            return tp + " bit/s";
        }

        tp = tp / 1024;

        if (tp < 1024) {
            return tp + " Kbit/s";
        }

        tp = tp / 1024;
        return tp + " Mbit/s";
    }

    private void addConnection(String from, String to, String id, String tp) {

        // This should be unique!
        String tmp = from + id;

        ConnectionInfo c = (ConnectionInfo) oldCons.remove(tmp);

        if (c == null) {

            try {
                c = new ConnectionInfo(DirectSocketAddress.getByAddress(from),
                        DirectSocketAddress.getByAddress(to), id, Long
                                .parseLong(tp));
            } catch (Exception e) {
                System.out.println("Failed to create ConnectionInfo " + e);
            }
        }

        if (c != null) {
            cons.put(tmp, c);
        }
    }

    private void parseConnections(StringTokenizer t) {

        int connections = Integer.parseInt(t.nextToken());

        mouseOverText.add("Connections: " + connections);

        if (connections == 0) {
            mouseOverText.add("Throughput : 0 Mbit/s");
            setMouseOverText((String[]) mouseOverText.toArray(new String[0]));
            return;
        }

        HashMap<String, ConnectionInfo> tmp = cons;
        cons = oldCons;
        oldCons = tmp;

        try {
            for (int i = 0; i < connections; i++) {

                String from = t.nextToken();
                String to = t.nextToken();
                String id = t.nextToken();
                String tp = t.nextToken();

                addConnection(from, to, id, tp);
            }

            long totalTP = Long.parseLong(t.nextToken());
            mouseOverText.add("Throughput : " + convert(totalTP));

        } catch (Exception e) {
            System.out.println("Oops: " + e);
        }

        cleanupOldConnections();
    }

    private void cleanupOldConnections() {

        if (oldCons.size() > 0) {

            Iterator itt = oldCons.values().iterator();

            while (itt.hasNext()) {
                ConnectionInfo i = (ConnectionInfo) itt.next();

                if (i.edge1 != null) {
                    // TODO: ugly!
                    hub.deleteEdge(i.edge1);
                }

                if (i.edge2 != null) {
                    // TODO: ugly!
                    hub.deleteEdge(i.edge2);
                }
            }

            oldCons.clear();
        }
    }

    public void update(ClientInfo info) {

        String adr = info.getClientAddress().toString();

        mouseOverText.add("Router     : " + adr);

        String stats = info.getProperty("statistics");

        if (stats == null || stats.length() == 0) {
            mouseOverText.add("Connections: 0");
            mouseOverText.add("Throughput : 0 Mbit/s");
            setMouseOverText((String[]) mouseOverText.toArray(new String[0]));

        } else {

            StringTokenizer t = new StringTokenizer(stats, ", ");

            if (t.countTokens() == 0) {
                // System.out.println("Got junk in router statistics! " +
                // stats);
                mouseOverText.add("Connections: 0");
                mouseOverText.add("Throughput : 0 Mbit/s");
                setMouseOverText((String[]) mouseOverText
                        .toArray(new String[0]));
            } else {
                parseConnections(t);
            }
        }

        setMouseOverText((String[]) mouseOverText.toArray(new String[0]));
        mouseOverText.clear();
    }

    public void showConnections(HashMap clients) {

        // System.out.println("Updating router connections!");

        Iterator itt = cons.values().iterator();

        while (itt.hasNext()) {

            ConnectionInfo c = (ConnectionInfo) itt.next();

            if (c.edge1 == null) {
                // try to find the nodes the router connects

                ClientNode from = (ClientNode) clients.get(c.from);

                if (from != null) {

                    // System.out.println("Adding edge: " + c.from + " to
                    // router");

                    c.edge1 = new Edge(from, this);
                    c.edge1.setColor(Color.LTGRAY);
                } else {
                    System.out.println("Could not add edge: " + c.from
                            + " to router");
                }
            }

            if (c.edge2 == null) {
                // try to find the nodes the router connects

                ClientNode to = (ClientNode) clients.get(c.to);

                if (to != null) {
                    //     
                    // System.out.println("Adding edge: router to " + c.to);

                    c.edge2 = new Edge(this, to);
                    c.edge2.setColor(Color.LTGRAY);
                } else {
                    System.out.println("Could not add edge: router to " + c.to);
                    System.out.println("Clients:");
                    System.out.println(clients.toString());
                    System.out.println("\n\n\n");
                }
            }

            if (c.edge1 != null && !c.edge1.isVisible()) {
                // System.out.println("Showing edge: " + c.from + " to router");
                hub.showEdge(c.edge1);
            }

            if (c.edge2 != null && !c.edge2.isVisible()) {
                // System.out.println("Showing edge: router to " + c.to);
                hub.showEdge(c.edge2);
            }
        }
    }
}
