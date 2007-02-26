package smartsockets.viz;

import java.awt.Color;
import java.awt.Frame;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;

import smartsockets.direct.DirectServerSocket;
import smartsockets.direct.DirectSocketFactory;
import smartsockets.direct.DirectSocketAddress;
import smartsockets.hub.servicelink.ClientInfo;
import smartsockets.hub.servicelink.HubInfo;
import smartsockets.hub.servicelink.ServiceLink;

import com.touchgraph.graphlayout.Edge;
import com.touchgraph.graphlayout.GLPanel;
import com.touchgraph.graphlayout.Node;
import com.touchgraph.graphlayout.TGException;

/** GLPanel contains code for adding scrollbars and interfaces to the TGPanel
 * The "GL" prefix indicates that this class is GraphLayout specific, and
 * will probably need to be rewritten for other applications.
 *
 * @author   Alexander Shapiro
 * @version  1.22-jre1.1  $Id: GLPanel.java,v 1.3 2002/09/23 18:45:56 ldornbusch Exp $
 */
public class SmartsocketsViz extends GLPanel implements Runnable {

    private static final long serialVersionUID = -3629362794531051537L;

    private ServiceLink sl;

    private HashMap<DirectSocketAddress, HubNode> hubs = 
        new HashMap<DirectSocketAddress, HubNode>();
    
    private HashMap<DirectSocketAddress, HubNode> oldHubs = 
        new HashMap<DirectSocketAddress, HubNode>();

    /** Default constructor.
     * @param hub 
     */
    public SmartsocketsViz(DirectSocketAddress hub) {
        super();

        tgPanel.setBackground(Color.BLACK);
        
        initPopups();
        
        try {
            DirectSocketFactory df = DirectSocketFactory.getSocketFactory();
            DirectServerSocket ss = df.createServerSocket(0, 1, null);

            sl = ServiceLink.getServiceLink(null, hub, ss.getAddressSet());
            sl.registerProperty("visualization", "");                        
        } catch (Exception e) {
            System.err.println("Failed to connect to Hub: " + e);
            e.printStackTrace(System.err);
            System.exit(1);
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
//              JDK11 Change .. because of MenuBecomesInvisible
                tgPanel.setMaintainMouseOver(false);
                tgPanel.setMouseOverN(null);
                tgPanel.repaint();
//              JDK11 Change .. because of MenuBecomesInvisible
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
//              JDK11 Change .. because of MenuBecomesInvisible
                tgPanel.setMaintainMouseOver(false);
                tgPanel.setMouseOverN(null);
                tgPanel.repaint();
//              JDK11 Change .. because of MenuBecomesInvisible
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

        HubNode h = (HubNode) oldHubs.remove(info.hubAddress);

        if (h == null) {
            
            System.out.println("Found new hub " + info.hubAddress.toString());
            
            h = new HubNode(this, info);
                        
            try {              
                tgPanel.addNode(h);
            } catch (TGException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        } else { 
            System.out.println("Updating hub " + info.hubAddress.toString());                        
            h.updateInfo(info);
        }

        hubs.put(info.hubAddress, h);
    }
    
  
    private void updateGraph() {

        System.out.println("Retrieving graph ...");
        
        HubInfo [] p = getHubs();

        System.out.println("Retrieving graph done!");
                
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
            n.updateEdges();
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
    }

    public void run() {

        while (true) {
            updateGraph();

            try {
                Thread.sleep(5000);
            } catch (Exception e) {
                // ignore
            }
        }
    }
   
    public static void main(String[] args) {

        if (args.length != 1) {
            System.err.println("Hub address required as a parameter...");
            System.exit(1);
        }

        DirectSocketAddress hub = null;

        try {
            hub = DirectSocketAddress.getByAddress(args[0]);
        } catch (Exception e1) {
            System.err.println("Coudn't parse hub address: " + args[0]);
            System.exit(1);
        }

        final Frame frame;
        final SmartsocketsViz glPanel = new SmartsocketsViz(hub);
        frame = new Frame("TouchGraph GraphLayout");
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                frame.remove(glPanel);
                frame.dispose();
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
}
