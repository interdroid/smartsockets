package ibis.smartsockets.viz;

import ibis.smartsockets.direct.DirectSocketAddress;
import ibis.smartsockets.hub.servicelink.ClientInfo;
import ibis.smartsockets.hub.servicelink.HubInfo;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.touchgraph.graphlayout.Edge;
import com.touchgraph.graphlayout.Node;

public class HubNode extends SmartNode {

    private static Logger logger = LoggerFactory.getLogger(HubNode.class);

    final SmartsocketsViz parent;

    private HubInfo info;

    private HashMap<DirectSocketAddress, Edge> edges = new HashMap<DirectSocketAddress, Edge>();

    private HashMap<Object, ClientNode> clients = new HashMap<Object, ClientNode>();

    private CollectionClientNode clientCollection;

    private boolean collapseClients = false;

    public HubNode(SmartsocketsViz parent, HubInfo info) {
        super(info.hubAddress.toString());

        setPopup("CollapsedHubNode");

        if (parent.isCompact()) {
            setType(Node.TYPE_ELLIPSE);
        } else {
            setType(Node.TYPE_ROUNDRECT);
        }

        // default color
        setPattern(parent.getUniqueColor());
        // default label
        if (parent.isCompact()) {
            setLabel("H");
        } else {
            setLabel("Hub");
        }

        setRank(0);

        // default text
        setMouseOverText(new String[] { "Hub", info.hubAddress.toString() });

        this.parent = parent;

        updateInfo(info);

        clientCollection = new CollectionClientNode(info.clients, this);
    }

    public void deleteEdge(Edge e) {
        parent.deleteEdge(e);
    }

    public void showEdge(Edge e) {
        parent.addEdge(e);
    }

    public void removeUnusedEdges() {

        HashMap<DirectSocketAddress, Edge> oldEdges = edges;
        edges = new HashMap<DirectSocketAddress, Edge>();

        // System.out.println("Hub " + info.hubAddress.toString()
        // + " connected to " + info.connectedTo.length + " others");

        // Refresh existing edges and add new ones..
        for (int i = 0; i < info.connectedTo.length; i++) {

            DirectSocketAddress to = info.connectedTo[i];

            Edge e = oldEdges.remove(to);

            if (e != null) {
                edges.put(to, e);
            }
        }

        // remove old edges
        if (oldEdges.size() > 0) {
            for (Edge edge : oldEdges.values()) {
                parent.deleteEdge(edge);
            }
        }
    }

    public void addAndUpdateEdges() {

        // Refresh existing edges and add new ones..
        for (int i = 0; i < info.connectedTo.length; i++) {

            DirectSocketAddress to = info.connectedTo[i];

            Edge e = edges.get(to);

            if (e == null) {

                HubNode other = parent.getHubNode(to);

                if (other != null) {
                    // we know the target

                    Edge e2 = other.edges.get(info.hubAddress);

                    if (e2 != null) {
                        // The other node already has an edge pointing to me, so
                        // we reuse that one!
                        e2.useArrowHead(false);

                        if (info.usingSSH[i]) {
                            e2.setColor(Color.red);
                        } else {
                            e2.setColor(Color.blue);
                        }
                        // parent.addEdge(e2);
                    } else {
                        e = new Edge(this, other, 200);
                        e.useArrowHead(true);

                        if (info.usingSSH[i]) {
                            e.setColor(Color.red);
                        } else {
                            e.setColor(Color.blue);
                        }

                        parent.addEdge(e);
                    }
                } else {
                    System.out.println("Failed to find hub: " + to);
                }
            }

            if (e != null) {
                edges.put(to, e);
            }
        }
    }


    //this was unused.
//    public void updateEdges() {
//
//        HashMap<DirectSocketAddress, Edge> oldEdges = edges;
//        edges = new HashMap<DirectSocketAddress, Edge>();
//
//        // System.out.println("Hub " + info.hubAddress.toString()
//        // + " connected to " + info.connectedTo.length + " others");
//
//        // Refresh existing edges and add new ones..
//        for (int i = 0; i < info.connectedTo.length; i++) {
//
//            DirectSocketAddress to = info.connectedTo[i];
//
//            Edge e = oldEdges.remove(to);
//
//            if (e == null) {
//
//                HubNode other = parent.getHubNode(to);
//
//                if (other != null) {
//                    // we know the target
//
//                    e = new Edge(this, other);
//                    e.useArrowHead(true);
//                    parent.addEdge(e);
//                } else {
//                    System.out.println("Failed to find hub: " + to);
//                }
//            }
//
//            if (e != null) {
//                edges.put(to, e);
//            }
//        }
//
//        // remove old edges
//        if (oldEdges.size() > 0) {
//            for (Edge edge : oldEdges.values()) {
//                parent.deleteEdge(edge);
//            }
//        }
//    }

    public synchronized void updateClients() {

        HashMap<Object, ClientNode> old = clients;
        clients = new HashMap<Object, ClientNode>();

        if (info.clients > 0) {

            if (collapseClients) {
                // fold all clients into 1 node to prevent clutter on the
                // screen
                clientCollection.setClients(info.clients);

                CollectionClientNode tmp = (CollectionClientNode) old
                        .remove("collection");

                if (tmp == null) {
                    // the collection wasn't shown yet!
                    parent.addNode(clientCollection);
                    parent.addEdge(clientCollection.getEdge());
                }

                clients.put("collection", clientCollection);

            } else {
                ClientInfo[] cs = parent.getClientsForHub(info.hubAddress);

                if (cs != null) {

                    for (int c = 0; c < cs.length; c++) {

                        DirectSocketAddress a = cs[c].getClientAddress();

                        ClientNode ci = old.remove(a);

                        if (ci == null) {
                            ci = new ClientNode(cs[c], this, parent.isCompact());

                            if (ci.isVisible()) {
                                parent.addNode(ci);
                                parent.addEdge(ci.getEdge());
                            }
                        } else {
                            ci.update(cs[c], this);

                            if (!ci.isVisible()) {
                                parent.deleteNode(ci);
                                parent.deleteEdge(ci.getEdge());
                            }
                        }

                        clients.put(a, ci);
                    }
                }
            }
        }

        // Now remove all leftover clients...
        if (old.size() > 0) {
            for (ClientNode ci : old.values()) {
                if (ci.getEdge() != null) {
                    parent.deleteEdge(ci.getEdge());
                }

                parent.deleteNode(ci);
            }
        }
    }

    public void delete() {

        // remove edges to other hubs
        if (edges.size() > 0) {
            for (Edge edge : edges.values()) {
                parent.deleteEdge(edge);
            }
        }

        synchronized (this) {
            // delete clients
            if (clients.size() > 0) {
                for (ClientNode n : clients.values()) {

                    if (n.getEdge() != null) {
                        parent.deleteEdge(n.getEdge());
                    }

                    parent.deleteNode(n);
                }
            }
        }

        parent.deleteNode(this);
    }

    public synchronized void updateInfo(HubInfo info) {
        this.info = info;

        logger.debug("Updating info for hub: \"" + info.vizInfo + "\"");

        if (info.vizInfo != null && info.vizInfo.length() > 0) {
            // double escape ^ char
            String[] split = info.vizInfo.split("\\^");

            // short label included
            if (split.length >= 1) {
                if (split[0].length() > 1) {
                    setLabel(" " + split[0].substring(0, 1) + " ");
                } else {
                    setLabel(" " + split[0] + " ");
                }
            }

            // long label included, override previously set label if
            // we are not "compact"
            if (split.length >= 2 && !parent.isCompact()) {
                setLabel(split[1]);
            }

            if (split.length >= 3) {
                // popup included (split on comma too)
                ArrayList<String> list = new ArrayList<String>();
                list.addAll(Arrays.asList(split[2].split(",")));
                list.add(info.hubAddress.toString());

                setMouseOverText(list.toArray(new String[0]));
            } else {
                // default
                setMouseOverText(new String[] { "Hub: " + info.name,
                        "Loc: " + info.hubAddress.toString() });
            }

            // color included
            if (split.length >= 4) {
                Color color = Color.decode(split[3]);

                setPattern(color);
            }

            // rank included
            if (split.length >= 5) {
                try {
                    int r = Integer.parseInt(split[4], 10);
                    setRank(r);
                } catch (NumberFormatException e) {
                    // ignored
                }
            }
        }

        // "Color: " + pattern.id});
    }

    public synchronized void expandClients() {
        this.collapseClients = false;
        setPopup("ExpandedHubNode");
        updateClients();
    }

    public synchronized void collapseClients() {
        this.collapseClients = true;
        setPopup("CollapsedHubNode");
        updateClients();
    }

    public synchronized void updateRouters(
            HashMap<Object, ClientNode> allClients) {

        if (collapseClients) {
            return;
        }

        if (clients.size() > 0) {

            for (ClientNode n : clients.values()) {

                if (n instanceof RouterClientNode) {
                    ((RouterClientNode) n).showConnections(allClients);
                }
            }
        }
    }

    public synchronized void getClients(HashMap<Object, ClientNode> target) {
        target.putAll(clients);
    }
}
