package ibis.smartsockets.viz.android;

import ibis.smartsockets.direct.DirectServerSocket;
import ibis.smartsockets.direct.DirectSocketAddress;
import ibis.smartsockets.direct.DirectSocketFactory;
import ibis.smartsockets.hub.servicelink.ClientInfo;
import ibis.smartsockets.hub.servicelink.HubInfo;
import ibis.smartsockets.hub.servicelink.ServiceLink;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import android.content.Context;
import android.graphics.Color;

import com.touchgraph.graphlayout.Edge;
import com.touchgraph.graphlayout.GLPanel;
import com.touchgraph.graphlayout.Node;
import com.touchgraph.graphlayout.TGException;

/**
 * GLPanel contains code for adding scrollbars and interfaces to the TGPanel The
 * "GL" prefix indicates that this class is GraphLayout specific, and will
 * probably need to be rewritten for other applications.
 * 
 * @author Alexander Shapiro
 * @version 1.22-jre1.1 $Id: GLPanel.java,v 1.3 2002/09/23 18:45:56 ldornbusch
 *          Exp $
 */
public final class SmartsocketsViz extends GLPanel implements Runnable {

    private static final long serialVersionUID = -3629362794531051537L;

    private ServiceLink sl;

    private HashMap<DirectSocketAddress, HubNode> hubs = new HashMap<DirectSocketAddress, HubNode>();

    private HashMap<DirectSocketAddress, HubNode> oldHubs = new HashMap<DirectSocketAddress, HubNode>();

    private UniquePaint c = new UniquePaint();

    private boolean done = false;

    private boolean finished = false;

    /**
     * Default constructor.
     * 
     * @param hub
     */
    public SmartsocketsViz(DirectSocketAddress hub, Context context,
            boolean showViz) {
        super(context);

        tgPanel.setBackColor(Color.BLACK);

        try {
            DirectSocketFactory df = DirectSocketFactory.getSocketFactory();
            DirectServerSocket ss = df.createServerSocket(0, 1, null);
            List<DirectSocketAddress> hubList = new ArrayList<DirectSocketAddress>();
            hubList.add(hub);
            sl = ServiceLink.getServiceLink(null, hubList, ss.getAddressSet());
            sl.registerProperty("smartsockets.viz", "V^Visualization^#545454");
        } catch (Exception e) {
            throw new Error("Failed to connect to Hub: ", e);
        }

        new Thread(this).start();
    }

    private synchronized HubInfo[] getHubs() {
        try {
            return sl.hubDetails();
        } catch (IOException e) {
            System.err.println("Failed to list hubs: " + e);
            e.printStackTrace(System.err);
            return null;
        }
    }

    synchronized ClientInfo[] getClientsForHub(DirectSocketAddress hub) {
        try {
            return sl.clients(hub);
        } catch (IOException e) {
            System.err.println("Failed to list hubs: " + e);
            e.printStackTrace(System.err);
            return null;
        }
    }

    private void updateHub(HubInfo info) {

        HubNode h = (HubNode) oldHubs.remove(info.hubAddress);

        if (h == null) {

            // System.out.println("Found new hub " +
            // info.hubAddress.toString());

            h = new HubNode(this, info);

            try {
                tgPanel.addNode(h);
            } catch (TGException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        } else {
            // System.out.println("Updating hub " + info.hubAddress.toString());
            h.updateInfo(info);
        }

        hubs.put(info.hubAddress, h);
    }

    private void updateGraph() {

        // System.out.println("Retrieving graph ...");

        HubInfo[] p = getHubs();

        // System.out.println("Retrieving graph done!");

        if (p == null) {
            return;
        }

        // Flip the hashmaps
        HashMap<DirectSocketAddress, HubNode> tmp = hubs;
        hubs = oldHubs;
        oldHubs = tmp;

        for (int i = 0; i < p.length; i++) {
            updateHub(p[i]);
        }

        if (oldHubs.size() > 0) {
            Iterator itt = oldHubs.values().iterator();

            while (itt.hasNext()) {
                HubNode hi = (HubNode) itt.next();
                hi.delete();
            }
        }

        // Now update the connections between the hubs and the clients that
        // are connected to them...
        for (HubNode n : hubs.values()) {
            n.removeUnusedEdges();
            n.addAndUpdateEdges();
            // n.updateEdges();
            n.updateClients();
        }

        // Finally update router information (which may depend on information of
        // other clients
        HashMap<Object, ClientNode> clients = new HashMap<Object, ClientNode>();

        for (HubNode n : hubs.values()) {
            n.getClients(clients);
        }

        for (HubNode n : hubs.values()) {
            n.updateRouters(clients);
        }

        tgPanel.postInvalidate();
    }

    public synchronized void done() {
        done = true;
        notifyAll();
    }

    public synchronized boolean getDone() {
        return done;
    }

    private synchronized void waitFor(long time) {
        try {
            wait(time);
        } catch (InterruptedException e) {
            // ignore
        }
    }

    public synchronized void run() {
        while (!getDone()) {
            updateGraph();
            waitFor(5000);
        }
        try {
            sl.closeVirtualConnection(0L);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        sl.setDone();

        finished = true;
        notifyAll();
    }

    public synchronized void waitUntilFinished() {
        while (!finished) {
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void addEdge(Edge e) {
        tgPanel.addEdge(e);
    }

    public void deleteEdge(Edge e) {
        tgPanel.deleteEdge(e);
    }

    public HubNode getHubNode(DirectSocketAddress to) {
        return (HubNode) hubs.get(to);
    }

    public void addNode(Node n) {
        try {
            tgPanel.addNode(n);
        } catch (TGException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void deleteNode(Node node) {
        tgPanel.deleteNode(node);
    }

    public Pattern getUniquePaint() {
        return c.getUniquePaint();
    }
}
