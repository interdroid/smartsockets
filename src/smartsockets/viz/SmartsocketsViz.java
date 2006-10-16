/*
 * TouchGraph LLC. Apache-Style Software License
 *
 *
 * Copyright (c) 2001-2002 Alexander Shapiro. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by
 *        TouchGraph LLC (http://www.touchgraph.com/)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "TouchGraph" or "TouchGraph LLC" must not be used to endorse
 *    or promote products derived from this software without prior written
 *    permission.  For written permission, please contact
 *    alex@touchgraph.com
 *
 * 5. Products derived from this software may not be called "TouchGraph",
 *    nor may "TouchGraph" appear in their name, without prior written
 *    permission of alex@touchgraph.com.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL TOUCHGRAPH OR ITS CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * ====================================================================
 *
 */

package smartsockets.viz;

import java.awt.Color;
import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;

import smartsockets.direct.DirectServerSocket;
import smartsockets.direct.DirectSocketFactory;
import smartsockets.direct.SocketAddressSet;
import smartsockets.hub.servicelink.Client;
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

    class HubInfo {
        Node node;

        SocketAddressSet address;

        HashMap clients;
    }

    class ClientInfo {
        Node node;

        Edge edge;

        HubInfo proxy;

        Client client;
    }

    private ServiceLink sl;

    private HashMap hubs = new HashMap();
    private HashMap oldHubs = new HashMap();

    /** Default constructor.
     * @param hub 
     */
    public SmartsocketsViz(SocketAddressSet hub) {
        super();

        try {
            DirectSocketFactory df = DirectSocketFactory.getSocketFactory();
            DirectServerSocket ss = df.createServerSocket(0, 1, null);

            sl = ServiceLink.getServiceLink(hub, ss.getAddressSet());
            sl.registerService("visualization", "");                        
        } catch (Exception e) {
            System.err.println("Failed to connect to Hub: " + e);
            e.printStackTrace(System.err);
            System.exit(1);
        }

        new Thread(this).start();
    }

    private SocketAddressSet[] getHubs() {
        try {
            return sl.proxies();
        } catch (IOException e) {
            System.err.println("Failed to list hubs: " + e);
            e.printStackTrace(System.err);
            return null;
        }
    }

    private Client[] getClientsForHub(SocketAddressSet hub) {
        try {
            return sl.clients(hub);
        } catch (IOException e) {
            System.err.println("Failed to list hubs: " + e);
            e.printStackTrace(System.err);
            return null;
        }
    }

    private void updateHub(SocketAddressSet a) {

        HubInfo h = (HubInfo) oldHubs.remove(a);

        if (h == null) {
            h = new HubInfo();
            h.address = a;

            try {
                h.node = new Node(a.toString(), " H ");
                h.node.setType(Node.TYPE_CIRCLE);
                h.node.setBackColor(Color.decode("#8B2500"));
                h.node.setNodeBorderInactiveColor(Color.decode("#5c1800"));

                h.node.setMouseOverText(new String[] { "Hub:", a.toString() });

                tgPanel.addNode(h.node);
            } catch (TGException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            h.clients = new HashMap();
        }

        Client[] clients = getClientsForHub(a);

        updateClients(clients, h);

        hubs.put(a, h);
    }

    private ClientInfo createClientInfo(Client c, HubInfo h) {

        ClientInfo ci = new ClientInfo();
        ci.client = c;
        ci.proxy = h;

        String adr = c.getClientAddress().toString();

        String[] mouseOverText = null;
        Color color = null;
        Color border = null;
        String label = null;

        if (c.offersService("router")) {
            System.out.println("Adding router " + adr);
            mouseOverText = new String[] { "Router:", adr };
            color = Color.decode("#FF7F24"); // Color.decode("#CDC673");
            border = Color.decode("#CD661D"); // Color.decode("#8B864E");
            label = "R";
        } else if (c.offersService("visualization")) {
            System.out.println("Adding visualization " + adr);
            mouseOverText = new String[] { "Visualization:", adr };
            color = Color.decode("#8000A0");
            border = Color.decode("#54006A"); 
            label = "V";
        } else {
            System.out.println("Adding client " + adr);
            mouseOverText = new String[] { "Client:", adr };
            label = "C";
        }

        try {
            ci.node = (Node) tgPanel.addNode(adr, label);
            ci.node.setType(Node.TYPE_CIRCLE);

            if (color != null) {
                ci.node.setBackColor(color);
            }

            if (border != null) {
                ci.node.setNodeBorderInactiveColor(border);
            }

            if (mouseOverText != null) {
                ci.node.setMouseOverText(mouseOverText);
            }

            ci.edge = tgPanel.addEdge(ci.node, h.node, Edge.DEFAULT_LENGTH);
        } catch (TGException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return null;
        }

        return ci;
    }

    private void updateClients(Client[] clients, HubInfo h) {

        if (h.clients.size() == 0) {
            // No clients yet, so just all them all....

            for (int c = 0; c < clients.length; c++) {
                ClientInfo ci = createClientInfo(clients[c], h);

                if (ci != null) {
                    h.clients.put(clients[c].getClientAddress(), ci);
                }
            }

            return;
        }

        // Do a diff between the old and new client set...
        HashMap old = h.clients;
        h.clients = new HashMap();

        for (int c = 0; c < clients.length; c++) {

            ClientInfo ci = (ClientInfo) old.remove(clients[c]
                    .getClientAddress());

            if (ci == null) {
                ci = createClientInfo(clients[c], h);
            }

            h.clients.put(clients[c].getClientAddress(), ci);
        }

        if (old.size() != 0) {

            Iterator itt = old.values().iterator();

            while (itt.hasNext()) {

                ClientInfo ci = (ClientInfo) itt.next();

                System.out.println("Removing client "
                        + ci.client.getClientAddress().toString());

                if (ci.edge != null) {
                    tgPanel.deleteEdge(ci.edge);
                }

                tgPanel.deleteNode(ci.node);
            }
        }
    }

    private void updateGraph() {

        SocketAddressSet[] p = getHubs();

        if (p == null) {
            return;
        }

        // Flip the hashmaps 
        HashMap tmp = hubs;
        hubs = oldHubs;
        oldHubs = tmp;

        for (int i = 0; i < p.length; i++) {
            updateHub(p[i]);
        }

        if (oldHubs.size() > 0) {
            Iterator itt = oldHubs.values().iterator();

            while (itt.hasNext()) {

                // TODO: clean this up!!
                HubInfo hi = (HubInfo) itt.next();

                //if (ci.edge != null) {                     
                //    tgPanel.deleteEdge(ci.edge);
                //} 

                tgPanel.deleteNode(hi.node);
            }
        }

        /*
         Node r = tgPanel.getGES().getRandomNode();

         if (r.getLabel().equals("C")) {

         for (int i=r.edgeCount()-1;i>=0;i--) { 
         tgPanel.deleteEdge(r.edgeAt(i));
         }

         tgPanel.deleteNode(r);
         }
         */
    }

    public void run() {

        while (true) {
            updateGraph();

            try {
                Thread.sleep(10000);
            } catch (Exception e) {
                // ignore
            }
        }
    }

    public void randomGraph() throws TGException {
        /*
         Node hub = tgPanel.addNode("hub1", " H ");
         hub.setType(Node.TYPE_CIRCLE);
         hub.setMouseOverText(
         new String [] { "Hub at:", 
         "82.161.4.24/192.168.1.35-17878" });
         
         for ( int i=0; i<7; i++ ) {
         Node client = tgPanel.addNode("client of hub1" + i, "C");            
         client.setType(Node.TYPE_ROUNDRECT);            
         Edge edge = new Edge(client, hub, Edge.DEFAULT_LENGTH);
         tgPanel.addEdge(edge);            
         }
         
         Node hub2 = tgPanel.addNode("hub2", " H ");
         hub2.setType(Node.TYPE_CIRCLE);
         hub2.setMouseOverText(
         new String [] { "Hub at:", 
         "130.37.193.15/10.0.0.15-17878" });        
         
         for ( int i=0; i<5; i++ ) {
         Node client = tgPanel.addNode("client of hub2" + i, "C");            
         client.setType(Node.TYPE_ROUNDRECT);            
         Edge edge = new Edge(client, hub2, Edge.DEFAULT_LENGTH);
         tgPanel.addEdge(edge);            
         }
         
         Edge edge = new Edge(hub, hub2, Edge.DEFAULT_LENGTH);
         tgPanel.addEdge(edge);            
         
         
         /*
         TGForEachNode fen = new TGForEachNode() {
         public void forEachNode(Node n) {
         for(int i=0;i<5;i++) {
         Node r = tgPanel.getGES().getRandomNode();
         if(r!=n && tgPanel.findEdge(r,n)==null) 
         tgPanel.addEdge(r,n,Edge.DEFAULT_LENGTH);	
         }
         }
         };    	
         tgPanel.getGES().forAllNodes(fen);
         */
        /*
         tgPanel.setLocale(hub,1);
         tgPanel.setSelect(hub);
         try {
         Thread.currentThread().sleep(2000); 
         } catch (InterruptedException ex) {}                    				

         getHVScroll().slowScrollToCenter(hub);
         */
    }

    public static void main(String[] args) {

        if (args.length != 1) {
            System.err.println("Hub address required as a parameter...");
            System.exit(1);
        }

        SocketAddressSet hub = null;

        try {
            hub = new SocketAddressSet(args[0]);
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
}
