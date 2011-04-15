package ibis.smartsockets.viz;

import ibis.smartsockets.SmartSocketsProperties;
import ibis.smartsockets.direct.DirectServerSocket;
import ibis.smartsockets.direct.DirectSocketAddress;
import ibis.smartsockets.direct.DirectSocketFactory;
import ibis.smartsockets.hub.servicelink.ClientInfo;
import ibis.smartsockets.hub.servicelink.HubInfo;
import ibis.smartsockets.hub.servicelink.ServiceLink;
import ibis.smartsockets.util.TypedProperties;

import java.awt.Color;
import java.awt.Frame;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

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

    // if true, use compact (single letter) labels, if true use full labels
    private final boolean compact;

    private final ServiceLink sl;

    private HashMap<DirectSocketAddress, HubNode> hubs = new HashMap<DirectSocketAddress, HubNode>();

    private HashMap<DirectSocketAddress, HubNode> oldHubs = new HashMap<DirectSocketAddress, HubNode>();

    private UniqueColor c = new UniqueColor();

    private boolean done = false;

    private static Color getTextColor() {
        String textColorString = System
                .getProperty(SmartSocketsProperties.VIZ_TEXT_COLOR);

        if (textColorString == null) {
            return null;
        }
        return Color.decode(textColorString);

    }

    private static Color getBackgroundColor() {
        String backgroundColorString = System
                .getProperty(SmartSocketsProperties.VIZ_BACKGROUND_COLOR);

        if (backgroundColorString == null) {
            return null;
        }

        return Color.decode(backgroundColorString);
    }

    private static DirectSocketAddress[] getAddresses(String... hubs)
            throws Exception {
        DirectSocketAddress[] result = new DirectSocketAddress[hubs.length];
        for (int i = 0; i < hubs.length; i++) {
            result[i] = DirectSocketAddress.getByAddress(hubs[i]);
        }
        return result;
    }

    public SmartsocketsViz(boolean useSliders, List<DirectSocketAddress> hubs) {
        this(useSliders, hubs.toArray(new DirectSocketAddress[0]));
    }

    public SmartsocketsViz(List<DirectSocketAddress> hubs) {
        this(hubs.toArray(new DirectSocketAddress[0]));
    }

    public SmartsocketsViz(boolean useSliders, DirectSocketAddress... hubs) {
        this(getTextColor(), getBackgroundColor(), false, useSliders, true,
                hubs);
    }

    public SmartsocketsViz(DirectSocketAddress... hubs) {
        this(getTextColor(), getBackgroundColor(), false, hubs);
    }

    public SmartsocketsViz(boolean useSliders, String... hubs) throws Exception {
        this(getTextColor(), getBackgroundColor(), false, useSliders, true,
                getAddresses(hubs));
    }

    public SmartsocketsViz(String... hubs) throws Exception {
        this(getTextColor(), getBackgroundColor(), false, getAddresses(hubs));
    }

    public SmartsocketsViz(Color textColor, Color backgroundColor,
            boolean showself, DirectSocketAddress... hubs) {
        this(textColor, backgroundColor, showself, true, true, hubs);
    }
    
    public SmartsocketsViz(Color textColor, Color backgroundColor,
            boolean showself, boolean useSliders, boolean compact,
            String... hubs) throws Exception {
        this(textColor, backgroundColor, showself, useSliders, compact, getAddresses(hubs));
    }

    /**
     * Default constructor.
     * 
     */
    public SmartsocketsViz(Color textColor, Color backgroundColor,
            boolean showself, boolean useSliders, boolean compact,
            DirectSocketAddress... hubs) {
        super(textColor, backgroundColor, useSliders);

        this.compact = compact;

        initPopups();

        try {
            TypedProperties p = SmartSocketsProperties.getDefaultProperties();
            p.setProperty(SmartSocketsProperties.SSH_IN, "true");
            p.setProperty(SmartSocketsProperties.SSH_OUT, "true");

            DirectSocketFactory df = DirectSocketFactory.getSocketFactory(p);
            DirectServerSocket ss = df.createServerSocket(0, 1, null);

            List<DirectSocketAddress> hubList = Arrays.asList(hubs);

            sl = ServiceLink.getServiceLink(null, hubList, ss.getAddressSet());
            if (showself) {
                sl.registerProperty("smartsockets.viz",
                        "V^Visualization^#545454");
            } else {
                sl.registerProperty("smartsockets.viz", "invisible");
            }
        } catch (Exception e) {
            throw new Error("Failed to connect to Hub: ", e);
        }

        new Thread(this).start();
    }

    private void initPopups() {
        setNodePopup(null, null);
        initHubPopups();
    }

    private void initHubPopups() {

        PopupMenu p = new PopupMenu();

        MenuItem menuItem;
        menuItem = new MenuItem("Expand clients");

        ActionListener action = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (popupNode != null && popupNode instanceof HubNode) {
                    ((HubNode) popupNode).expandClients();
                }
                // JDK11 Change .. because of MenuBecomesInvisible
                tgPanel.setMaintainMouseOver(false);
                tgPanel.setMouseOverN(null);
                tgPanel.repaint();
                // JDK11 Change .. because of MenuBecomesInvisible
            }
        };

        menuItem.addActionListener(action);
        p.add(menuItem);
        setNodePopup("CollapsedHubNode", p);

        p = new PopupMenu();
        menuItem = new MenuItem("Collapse clients");

        action = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (popupNode != null && popupNode instanceof HubNode) {
                    ((HubNode) popupNode).collapseClients();
                }
                // JDK11 Change .. because of MenuBecomesInvisible
                tgPanel.setMaintainMouseOver(false);
                tgPanel.setMouseOverN(null);
                tgPanel.repaint();
                // JDK11 Change .. because of MenuBecomesInvisible
            }
        };

        menuItem.addActionListener(action);
        p.add(menuItem);
        setNodePopup("ExpandedHubNode", p);
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

        HubNode h = oldHubs.remove(info.hubAddress);

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
            for (HubNode hi : oldHubs.values()) {
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

        tgPanel.repaint();
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

    public void run() {
        while (!getDone()) {
            updateGraph();
            waitFor(5000);
        }

        sl.setDone();
    }

    public static void main(String[] args) {

        System.err.println("Starting Smartsockets Vizualization...");

        if (args.length != 1) {
            System.err.println("Hub address required as a parameter...");
            System.exit(1);
        }

        List<DirectSocketAddress> hub = new LinkedList<DirectSocketAddress>();

        for (String s : args) {
            try {
                hub.add(DirectSocketAddress.getByAddress(s));
            } catch (Exception e1) {
                System.err.println("Couldn't parse hub address: " + s);
            }
        }

        if (hub.size() == 0) {
            System.err.println("No hubs provided!");
            System.exit(1);
        }

        final Frame frame;

        final SmartsocketsViz glPanel = new SmartsocketsViz(hub);

        frame = new Frame("Smartsockets Visualization");
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                glPanel.done();
                frame.remove(glPanel);
                frame.dispose();
                System.exit(0); // otherwise it does'nt exit, there
                                // is a non-deamon thread in touchgraph.
                                // --Ceriel
            }
        });
        frame.add("Center", glPanel);
        frame.setSize(800, 600);
        frame.setVisible(true);
    }

    public void addEdge(Edge e) {
        tgPanel.addEdge(e);
    }

    public void deleteEdge(Edge e) {
        tgPanel.deleteEdge(e);
    }

    public HubNode getHubNode(DirectSocketAddress to) {
        return hubs.get(to);
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

    public Color getUniqueColor() {
        return c.getUniqueColor();
    }

    boolean isCompact() {
        return compact;
    }
}
