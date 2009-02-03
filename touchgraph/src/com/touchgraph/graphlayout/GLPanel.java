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

package com.touchgraph.graphlayout;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.MenuItem;
import java.awt.Panel;
import java.awt.Point;
import java.awt.PopupMenu;
import java.awt.Scrollbar;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;
import java.util.Hashtable;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SpringLayout;
import javax.swing.UIManager;
import javax.swing.text.StyledEditorKit.ForegroundAction;

import com.touchgraph.graphlayout.graphelements.TGForEachNode;
import com.touchgraph.graphlayout.interaction.GLEditUI;
import com.touchgraph.graphlayout.interaction.GLNavigateUI;
import com.touchgraph.graphlayout.interaction.HVScroll;
import com.touchgraph.graphlayout.interaction.RotateScroll;
import com.touchgraph.graphlayout.interaction.TGUIManager;
import com.touchgraph.graphlayout.interaction.ZoomScroll;

/**
 * GLPanel contains code for adding scrollbars and interfaces to the TGPanel The
 * "GL" prefix indicates that this class is GraphLayout specific, and will
 * probably need to be rewritten for other applications.
 * 
 * @author Alexander Shapiro
 * @version 1.22-jre1.1 $Id: GLPanel.java,v 1.3 2002/09/23 18:45:56 ldornbusch
 *          Exp $
 */
public class GLPanel extends JPanel {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public String zoomLabel = "Zoom"; // label for zoom menu item

    public String rotateLabel = "Rotate"; // label for rotate menu item

    public HVScroll hvScroll;

    public ZoomScroll zoomScroll;

    public RotateScroll rotateScroll;

    private Scrollbar horizontalSB;

    private Scrollbar verticalSB;

    private JSlider zoomSlider;

    private JSlider rotateSlider;

    private Panel topPanel;

    // The default popups
    public PopupMenu glPopup;

    public PopupMenu nodePopup;

    public PopupMenu edgePopup;

    private HashMap<String, PopupMenu> nodePopups = new HashMap<String, PopupMenu>();

    public Node popupNode;

    public Edge popupEdge;

    public Hashtable<String, JSlider> sliderHash; // = new Hashtable();

    protected TGPanel tgPanel;

    protected TGLensSet tgLensSet;

    protected TGUIManager tgUIManager;

    private JSlider currentSlider = null;

    private final Color textColor;

    private final Color backgroundColor;

    public GLPanel() {
        this(null, null);
    }

    /**
     * Default constructor.
     */
    public GLPanel(Color textColor, Color backgroundColor) {
        if (textColor == null) {
            textColor = Color.BLACK;
        }
        this.textColor = textColor;

        if (backgroundColor == null) {
            backgroundColor = Color.WHITE;
        }
        this.backgroundColor = backgroundColor;

        this.setForeground(this.textColor);
        this.setBackground(this.backgroundColor);

        setBorder(BorderFactory.createTitledBorder(""));
        sliderHash = new Hashtable<String, JSlider>();
        tgLensSet = new TGLensSet();
        tgPanel = new TGPanel(this.backgroundColor);

        tgPanel.setForeground(this.textColor);
        tgPanel.setBackground(this.backgroundColor);

        hvScroll = new HVScroll(tgPanel, tgLensSet);
        zoomScroll = new ZoomScroll(tgPanel);
        rotateScroll = new RotateScroll(tgPanel);
        initialize();
    }

    /**
     * Initialize panel, lens, and establish a random graph as a demonstration.
     */
    public void initialize() {
        buildPanel();

        setupDefaultBackgroundPopup();
        setUpDefaultNodePopup();
        setUpDefaultEdgePopup();

        buildLens();
        tgPanel.setLensSet(tgLensSet);
        addUIs();
        // tgPanel.addNode(); //Add a starting node.
        /*
         * try { randomGraph(); } catch ( TGException tge ) {
         * System.err.println(tge.getMessage());
         * tge.printStackTrace(System.err); }
         */
        setVisible(true);
    }

    /** Return the TGPanel used with this GLPanel. */
    public TGPanel getTGPanel() {
        return tgPanel;
    }

    // navigation .................

    /** Return the HVScroll used with this GLPanel. */
    public HVScroll getHVScroll() {
        return hvScroll;
    }

    /**
     * Sets the horizontal offset to p.x, and the vertical offset to p.y given a
     * Point <tt>p<tt>.
     */
    public void setOffset(Point p) {
        hvScroll.setOffset(p);
    };

    /** Return the horizontal and vertical offset position as a Point. */
    public Point getOffset() {
        return hvScroll.getOffset();
    };

    // rotation ...................

    /** Return the RotateScroll used with this GLPanel. */
    public RotateScroll getRotateScroll() {
        return rotateScroll;
    }

    /**
     * Set the rotation angle of this GLPanel (allowable values between 0 to
     * 359).
     */
    public void setRotationAngle(int angle) {
        rotateScroll.setRotationAngle(angle);
    }

    /** Return the rotation angle of this GLPanel. */
    public int getRotationAngle() {
        return rotateScroll.getRotationAngle();
    }

    // zoom .......................

    /** Return the ZoomScroll used with this GLPanel. */
    public ZoomScroll getZoomScroll() {
        return zoomScroll;
    }

    /**
     * Set the zoom value of this GLPanel (allowable values between -100 to
     * 100).
     */
    public void setZoomValue(int zoomValue) {
        zoomScroll.setZoomValue(zoomValue);
    }

    /** Return the zoom value of this GLPanel. */
    public int getZoomValue() {
        return zoomScroll.getZoomValue();
    }

    // ....

    public PopupMenu getGLPopup() {
        return glPopup;
    }

    public void buildLens() {
        tgLensSet.addLens(hvScroll.getLens());
        tgLensSet.addLens(zoomScroll.getLens());
        tgLensSet.addLens(rotateScroll.getLens());
        tgLensSet.addLens(tgPanel.getAdjustOriginLens());
    }

    public void buildPanel() {
        horizontalSB = hvScroll.getHorizontalSB();
        verticalSB = hvScroll.getVerticalSB();
        zoomSlider = zoomScroll.getZoomSlider();
        rotateSlider = rotateScroll.getRotateSlider();

        //setLayout(new BorderLayout());

        SpringLayout layout = new SpringLayout();
        
        setLayout(layout);

        Panel scrollPanel = new Panel();
        scrollPanel.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();

        Panel modeSelectPanel = new Panel();
        modeSelectPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));

        topPanel = new Panel();
        topPanel.setLayout(new GridBagLayout());
        c.gridy = 0;
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx = 0;
        c.weightx = 0;

        c.insets = new Insets(0, 0, 0, 0);
        c.gridy = 0;
        c.weightx = 1;

        sliderHash.put(zoomLabel, zoomSlider);
        sliderHash.put(rotateLabel, rotateSlider);

        JPanel scrollselect = scrollSelectPanel(new String[] { zoomLabel,
                rotateLabel });
        topPanel.add(scrollselect, c);



        c.fill = GridBagConstraints.BOTH;
        c.gridwidth = 1;
        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 1;
        c.weighty = 1;
        scrollPanel.add(tgPanel, c);

        c.gridx = 1;
        c.gridy = 1;
        c.weightx = 0;
        c.weighty = 0;
        // scrollPanel.add(verticalSB,c); // For WDR We do not need scrollbars

        c.gridx = 0;
        c.gridy = 2;
        // scrollPanel.add(horizontalSB,c); // For WDR We do not need scrollbars

        add(topPanel);
        add(scrollPanel);
        
        scrollPanel.setMinimumSize(new Dimension(200, 300));
        this.setMinimumSize(new Dimension(200, 300));
        topPanel.setMinimumSize(new Dimension(200, 300));
        
        layout.putConstraint(SpringLayout.WEST, scrollPanel, 0, SpringLayout.WEST,this);
        layout.putConstraint(SpringLayout.NORTH, scrollPanel, 0, SpringLayout.NORTH,this);
        layout.putConstraint(SpringLayout.SOUTH, scrollPanel, 0, SpringLayout.SOUTH,this);
        layout.putConstraint(SpringLayout.EAST, scrollPanel, 0, SpringLayout.EAST,this);

        
        layout.putConstraint(SpringLayout.EAST, topPanel, 0, SpringLayout.EAST,this);
        layout.putConstraint(SpringLayout.VERTICAL_CENTER, topPanel, 0, SpringLayout.VERTICAL_CENTER,this);

    }

    public void setBackgroundPopup(PopupMenu m) {
        remove(glPopup);
        glPopup = m;

        if (m != null) {
            add(m); // needed by JDK11 Popupmenu..
        }
    }

    public void setEdgePopup(PopupMenu m) {
        remove(edgePopup);
        edgePopup = m;

        if (m != null) {
            add(m); // needed by JDK11 Popupmenu..
        }
    }

    public void setNodePopup(String ID, PopupMenu m) {

        if (ID == null || ID.length() == 0) {
            remove(nodePopup);
            nodePopup = m;

            if (m != null) {
                add(m); // needed by JDK11 Popupmenu..
            }
        } else {

            PopupMenu old = (PopupMenu) nodePopups.remove(ID);

            if (old != null) {
                remove(old);
            }

            add(m);
            nodePopups.put(ID, m);

            // System.out.println("Added node popup: " + ID);
        }
    }

    private void setupDefaultBackgroundPopup() {

        glPopup = new PopupMenu();
        // add(glPopup); // needed by JDK11 Popupmenu..

        MenuItem menuItem = new MenuItem("Toggle Controls");
        ActionListener toggleControlsAction = new ActionListener() {
            boolean controlsVisible = true;

            public void actionPerformed(ActionEvent e) {
                controlsVisible = !controlsVisible;
                horizontalSB.setVisible(controlsVisible);
                verticalSB.setVisible(controlsVisible);
                topPanel.setVisible(controlsVisible);
                GLPanel.this.doLayout();
            }
        };
        menuItem.addActionListener(toggleControlsAction);
        // glPopup.add(menuItem);
    }

    private void setUpDefaultNodePopup() {
        nodePopup = new PopupMenu();
        add(nodePopup);
        MenuItem menuItem;

        menuItem = new MenuItem("Expand Node");
        ActionListener expandAction = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (popupNode != null) {
                    tgPanel.expandNode(popupNode);
                }
                // JDK11 Change .. because of MenuBecomesInvisible
                tgPanel.setMaintainMouseOver(false);
                tgPanel.setMouseOverN(null);
                tgPanel.repaint();
                // JDK11 Change .. because of MenuBecomesInvisible
            }
        };

        menuItem.addActionListener(expandAction);
        nodePopup.add(menuItem);

        menuItem = new MenuItem("Collapse Node");
        ActionListener collapseAction = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (popupNode != null) {
                    tgPanel.collapseNode(popupNode);
                }
                // JDK11 Change .. because of MenuBecomesInvisible
                tgPanel.setMaintainMouseOver(false);
                tgPanel.setMouseOverN(null);
                tgPanel.repaint();
                // JDK11 Change .. because of MenuBecomesInvisible
            }
        };

        menuItem.addActionListener(collapseAction);
        nodePopup.add(menuItem);

        menuItem = new MenuItem("Hide Node");
        ActionListener hideAction = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (popupNode != null) {
                    tgPanel.hideNode(popupNode);
                }
                // JDK11 Change .. because of MenuBecomesInvisible
                tgPanel.setMaintainMouseOver(false);
                tgPanel.setMouseOverN(null);
                tgPanel.repaint();
                // JDK11 Change .. because of MenuBecomesInvisible
            }
        };

        menuItem.addActionListener(hideAction);
        nodePopup.add(menuItem);

        menuItem = new MenuItem("Center Node");
        ActionListener centerAction = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (popupNode != null) {
                    getHVScroll().slowScrollToCenter(popupNode);
                }
                // JDK11 Change .. because of MenuBecomesInvisible
                tgPanel.setMaintainMouseOver(false);
                tgPanel.setMouseOverN(null);
                tgPanel.repaint();
                // JDK11 Change .. because of MenuBecomesInvisible
            }
        };
        menuItem.addActionListener(centerAction);
        nodePopup.add(menuItem);

        /*
         * nodePopup.addPopupMenuListener(new PopupMenuListener() { public void
         * popupMenuCanceled(PopupMenuEvent e) {} public void
         * popupMenuWillBecomeInvisible(PopupMenuEvent e) {
         * tgPanel.setMaintainMouseOver(false); tgPanel.setMouseOverN(null);
         * tgPanel.repaint(); } public void
         * popupMenuWillBecomeVisible(PopupMenuEvent e) {} });
         */

    }

    private void setUpDefaultEdgePopup() {
        edgePopup = new PopupMenu();
        add(edgePopup);
        MenuItem menuItem;

        menuItem = new MenuItem("Hide Edge");
        ActionListener hideAction = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (popupEdge != null) {
                    tgPanel.hideEdge(popupEdge);
                }
                // JDK11 Change .. because of MenuBecomesInvisible
                tgPanel.setMaintainMouseOver(false);
                tgPanel.setMouseOverN(null);
                tgPanel.repaint();
                // JDK11 Change .. because of MenuBecomesInvisible
            }
        };

        menuItem.addActionListener(hideAction);
        edgePopup.add(menuItem);

        /*
         * edgePopup.addPopupMenuListener(new PopupMenuListener() { public void
         * popupMenuCanceled(PopupMenuEvent e) {} public void
         * popupMenuWillBecomeInvisible(PopupMenuEvent e) {
         * tgPanel.setMaintainMouseOver(false); tgPanel.setMouseOverE(null);
         * tgPanel.repaint(); } public void
         * popupMenuWillBecomeVisible(PopupMenuEvent e) {} });
         */
    }

    protected JPanel scrollSelectPanel(final String[] sliderNames) {
        final JPanel sbp = new JPanel(new BorderLayout());
        sbp.setBackground(backgroundColor);
        sbp.setForeground(textColor);
        JPanel labelPanel = new JPanel(new GridLayout(1, 2));
        labelPanel.setBackground(backgroundColor);
        labelPanel.setForeground(textColor);
        JPanel sliderPanel = new JPanel(new GridLayout(1, 2));
        sliderPanel.setBackground(backgroundColor);
        sliderPanel.setForeground(textColor);

        // labelPanel
        // .add(new JLabel(
        // "Right-click nodes and background for more options"));

        for (int i = 0; i < sliderNames.length; i++) {
            JSlider slider = (JSlider) sliderHash.get(sliderNames[i]);
            slider.setBackground(backgroundColor);
            slider.setForeground(textColor);
            if (slider == null)
                continue;
            if (currentSlider == null)
                currentSlider = slider;
            JLabel label = new JLabel(sliderNames[i]);
            label.setBackground(backgroundColor);
            label.setForeground(textColor);
            labelPanel.add(label);
            sliderPanel.add(slider);
        }
        sbp.add(labelPanel, BorderLayout.NORTH);
        sbp.add(sliderPanel, BorderLayout.CENTER);

        return sbp;
    }

    public void addUIs() {
        tgUIManager = new TGUIManager();
        GLEditUI editUI = new GLEditUI(this);
        GLNavigateUI navigateUI = new GLNavigateUI(this);
        tgUIManager.addUI(editUI, "Edit");
        tgUIManager.addUI(navigateUI, "Navigate");
        tgUIManager.activate("Navigate");
    }

    public void randomGraph() throws TGException {
        Node n1 = tgPanel.addNode();
        n1.setType(0);
        for (int i = 0; i < 249; i++) {
            tgPanel.addNode();
        }

        TGForEachNode fen = new TGForEachNode() {
            public void forEachNode(Node n) {
                for (int i = 0; i < 5; i++) {
                    Node r = tgPanel.getGES().getRandomNode();
                    if (r != n && tgPanel.findEdge(r, n) == null)
                        tgPanel.addEdge(r, n, Edge.DEFAULT_LENGTH);
                }
            }
        };
        tgPanel.getGES().forAllNodes(fen);

        tgPanel.setLocale(n1, 1);
        tgPanel.setSelect(n1);
        try {
            Thread.sleep(2000);
        } catch (InterruptedException ex) {
        }

        getHVScroll().slowScrollToCenter(n1);
    }

    public static void main(String[] args) {

        final Frame frame;
        final GLPanel glPanel = new GLPanel();
        try {
            glPanel.randomGraph();
        } catch (TGException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
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

    public void showPopup(int x, int y) {
        popupNode = tgPanel.getMouseOverN();
        popupEdge = tgPanel.getMouseOverE();

        if (popupNode != null) {
            String popup = popupNode.getPopup();

            System.out.println("Got node popup: " + popup);

            if (popup == null || popup.length() == 0) {
                // show default popup

                if (nodePopup != null) {
                    tgPanel.setMaintainMouseOver(true);
                    nodePopup.show(tgPanel, x, y);
                }
            } else {
                PopupMenu p = (PopupMenu) nodePopups.get(popup);

                if (p != null) {
                    p.show(tgPanel, x, y);
                } else {
                    System.out.println("No popup defined for node: " + popup);
                }
            }
        } else if (popupEdge != null && edgePopup != null) {
            tgPanel.setMaintainMouseOver(true);
            edgePopup.show(tgPanel, x, y);
        } else if (glPopup != null) {
            // glPopup.show(tgPanel, x, y);
        }
    }

} // end com.touchgraph.graphlayout.GLPanel
