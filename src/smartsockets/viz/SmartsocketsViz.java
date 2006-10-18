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

import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;

import smartsockets.direct.DirectServerSocket;
import smartsockets.direct.DirectSocketFactory;
import smartsockets.direct.SocketAddressSet;
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

    private HubInfo[] getHubs() {
        try {
            return sl.hubDetails();
        } catch (IOException e) {
            System.err.println("Failed to list hubs: " + e);
            e.printStackTrace(System.err);
            return null;
        }
    }

    ClientInfo[] getClientsForHub(SocketAddressSet hub) {
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
            h = new HubNode(this, info);
                        
            try {              
                tgPanel.addNode(h);
            } catch (TGException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        } else { 
            h.updateInfo(info);
        }

        /*
         * Do this on demand ? 
        ClientInfo[] clients = getClientsForHub(info.hubAddress);
        updateClients(clients, h);
         */
        
        hubs.put(info.hubAddress, h);
    }

    private NormalClientNode createClientInfo(ClientInfo c, HubNode h) {

        NormalClientNode ci = new NormalClientNode(c, h);
       
        try {
            tgPanel.addNode(ci);
            tgPanel.addEdge(ci.getEdge());
        } catch (TGException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return null;
        }

        return ci;
    }

    /*
    private void updateClients(ClientInfo[] clients, HubNode h) {

        if (h.clients.size() == 0) {
            // No clients yet, so just all them all....

            for (int c = 0; c < clients.length; c++) {
                ClientNode ci = createClientInfo(clients[c], h);

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

            ClientNode ci = 
                (ClientNode) old.remove(clients[c].getClientAddress());

            if (ci == null) {
                ci = createClientInfo(clients[c], h);
            }

            h.clients.put(clients[c].getClientAddress(), ci);
        }

        if (old.size() != 0) {

            Iterator itt = old.values().iterator();

            while (itt.hasNext()) {

                ClientNode ci = (ClientNode) itt.next();

                System.out.println("Removing client "
                        + ci.client.getClientAddress().toString());

                if (ci.edge != null) {
                    tgPanel.deleteEdge(ci.edge);
                }

                tgPanel.deleteNode(ci.node);
            }
        }
    }
*/
    
    private void updateGraph() {

        System.out.println("Retrieving graph ...");
        
        HubInfo [] p = getHubs();

        System.out.println("Retrieving graph done!");
                
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
                HubNode hi = (HubNode) itt.next();                
                hi.delete();
            }
        }

        // Now update the connections between the hubs...
        Iterator itt = hubs.values().iterator();
        
        while (itt.hasNext()) {             
            ((HubNode) itt.next()).updateEdges();
        }
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
    
    public void addEdge(Edge e) {
        tgPanel.addEdge(e);
    }
    
    public void deleteEdge(Edge e) {
        tgPanel.deleteEdge(e);
    }

    public HubNode getHubNode(SocketAddressSet to) {
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
