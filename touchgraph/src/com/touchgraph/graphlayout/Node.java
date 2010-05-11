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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Node.
 * 
 * @author Alexander Shapiro
 * @author Murray Altheim (2001-11-06; added support for round rects and
 *         alternate Node colors)
 * @version 1.22-jre1.1 $Id: Node.java,v 1.2 2002/09/20 14:00:22 ldornbusch Exp
 *          $
 */
public class Node {

    /** This Node's type is a Rectangle. */
    public final static int TYPE_RECTANGLE = 1;

    /** This Node's type is a Round Rectangle. */
    public final static int TYPE_ROUNDRECT = 2;

    /** This Node's type is an Ellipse. */
    public final static int TYPE_ELLIPSE = 3;

    /** This Node's type is a Circle. */
    public final static int TYPE_CIRCLE = 4;

    public static final Font SMALL_TAG_FONT = new Font("Courier", Font.PLAIN, 9);
    
    private static final Color DEFAULT_BACK_FIXED_COLOR = new Color(255, 32, 20);
    
    private static final Color DEFAULT_BACK_SELECT_COLOR = new Color(255, 164, 0);
    
    private static final Color DEFAULT_BACK_DEFAULT_COLOR = Color.decode("#4080A0");
    
    private static final Color DEFAULT_BACK_HILIGHT_COLOR = new Color(205, 192, 166);
    
    private static final Color DEFAULT_BACK_MRF_COLOR = new Color(2, 35, 81);
    
    private static final Color DEFAULT_BACK_JML_COLOR = new Color(58, 176, 255);
    
    private static final Color DEFAULT_BORDER_DRAG_COLOR = new Color(130, 130, 180);
    
    private static final Color DEFAULT_BORDER_MOUSE_OVER_COLOR = new Color(160, 160, 180);

    private static final Color DEFAULT_BORDER_INACTIVE_COLOR = new Color(30, 50, 160);
    
    private static final Color DEFAULT_TEXT_COLOR = Color.black;
    
    // Variables that store default values for colors + fonts + node type
    public Color BACK_FIXED_COLOR = DEFAULT_BACK_FIXED_COLOR;

    public Color BACK_SELECT_COLOR = DEFAULT_BACK_SELECT_COLOR;

    public Color BACK_DEFAULT_COLOR = DEFAULT_BACK_DEFAULT_COLOR;

    public Color BACK_HILIGHT_COLOR = DEFAULT_BACK_HILIGHT_COLOR;

    public Color BACK_MRF_COLOR = DEFAULT_BACK_MRF_COLOR;

    public Color BACK_JML_COLOR = DEFAULT_BACK_JML_COLOR;

    public Color BORDER_DRAG_COLOR = DEFAULT_BORDER_DRAG_COLOR;

    public Color BORDER_MOUSE_OVER_COLOR = DEFAULT_BORDER_MOUSE_OVER_COLOR;

    public Color BORDER_INACTIVE_COLOR = DEFAULT_BORDER_INACTIVE_COLOR;

    public Color TEXT_COLOR = DEFAULT_TEXT_COLOR;

    public static Font TEXT_FONT = new Font("Verdana", Font.PLAIN, 10);

    public static Font TEXT_FONT_BOLD = new Font("Verdana Bold", Font.PLAIN, 10);

    public static int DEFAULT_TYPE = 1;

    /**
     * an int indicating the Node type.
     * 
     * @see TYPE_RECTANGLE
     * @see TYPE_ROUNDRECT
     * @see TYPE_ELLIPSE
     */
    protected int typ = TYPE_RECTANGLE;

    private String id;

    public double drawx;

    public double drawy;

    protected FontMetrics fontMetrics;

    protected Font font;

    protected String lbl;

    protected Color backColor = BACK_DEFAULT_COLOR;

    protected Color textColor = TEXT_COLOR;

    protected Paint backPaint = null;

    public double x;

    public double y;

    public double massfade = 1; // Used by layout

    protected double dx; // Used by layout

    protected double dy; // Used by layout

    protected boolean fixed;

    protected int repulsion; // Used by layout

    public boolean justMadeLocal = false;

    public boolean markedForRemoval = false;

    public int visibleEdgeCnt; // Should only be modified by

    // graphelements.VisibleLocality

    protected boolean visible;

    private Collection<Edge> edges;

    private String[] mouseOverText;

    private String popup;

    // ............

    // Modification by Lutz
    private String strUrl;

    private boolean antiAlias;
    
private static class EdgeIterator implements Iterator<Edge> {
        
        Edge[] edges;
        int i = 0;
        
        EdgeIterator(Edge[] edges) {
            this.edges = edges;
        }

        public boolean hasNext() {
            return i < edges.length;
        }

        public Edge next() {
            if (i >= edges.length) {
                throw new NoSuchElementException("Iterator exhausted");
            }
            return edges[i++];
        }

        public void remove() {
            throw new UnsupportedOperationException("remove not supported");
        }
    }
    
    private static class EdgeIterable implements Iterable<Edge> {
        Edge[] edges;
        EdgeIterable(Collection<Edge> edges) {
            this.edges = edges.toArray(new Edge[edges.size()]);
        }
        public Iterator<Edge> iterator() {
            return new EdgeIterator(edges);
        }
    }

    /**
     * Minimal constructor which will generate an ID value from Java's Date
     * class. Defaults will be used for type and color. The label will be taken
     * from the ID value.
     */
    public Node() {
        initialize(null);
        lbl = id;
        antiAlias = false;
    }

    /**
     * Constructor with the required ID <tt>id</tt>, using defaults for type
     * (rectangle), color (a static variable from TGPanel). The Node's label
     * will be taken from the ID value.
     */
    public Node(String id) {
        initialize(id);
        lbl = id;
        antiAlias = false;
    }

    /**
     * Constructor with Strings for ID <tt>id</tt> and <tt>label</tt>, using
     * defaults for type (rectangle) and color (a static variable from TGPanel).
     * If the label is null, it will be taken from the ID value.
     */
    public Node(String id, String label) {
        initialize(id);
        if (label == null) {
            lbl = id;
        } else {
            lbl = label;
        }
        antiAlias = false;
    }

    /**
     * Constructor with a String ID <tt>id</tt>, an int <tt>type</tt>,
     * Background Color <tt>bgColor</tt>, and a String <tt>label</tt>. If the
     * label is null, it will be taken from the ID value.
     * 
     * @see TYPE_RECTANGLE
     * @see TYPE_ROUNDRECT
     */
    public Node(String id, int type, Color color, String label) {
        initialize(id);
        typ = type;
        backColor = color;
        if (label == null) {
            lbl = id;
        } else {
            lbl = label;
        }
        antiAlias = false;
    }

    /**
     * Constructor with a String ID <tt>id</tt>, an int <tt>type</tt>,
     * Background Color <tt>bgColor</tt>, and a String <tt>label</tt>. If the
     * label is null, it will be taken from the ID value.
     * 
     * @see TYPE_RECTANGLE
     * @see TYPE_ROUNDRECT
     */
    public Node(String id, int type, Color color, String label,
            boolean antiAlias) {
        initialize(id);
        typ = type;
        backColor = color;
        if (label == null) {
            lbl = id;
        } else {
            lbl = label;
        }

        this.antiAlias = antiAlias;
    }

    private void initialize(String identifier) {
        this.id = identifier;
        edges = Collections.synchronizedSet(new HashSet<Edge>());
        x = Math.random() * 2 - 1; // If multiple nodes are added without
        // repositioning,
        y = Math.random() * 2 - 1; // randomizing starting location causes them
        // to spread out nicely.
        repulsion = 100;
        font = TEXT_FONT;
        fixed = false;
        typ = DEFAULT_TYPE;
        visibleEdgeCnt = 0;
        visible = false;
    }
    
    public Iterable<Edge> getEdgeIterable() {
        return new EdgeIterable(edges);
    }

    // setters and getters ...............

    public void setAntiAlias(boolean enabled) {
        this.antiAlias = enabled;
    }

    public void setNodeBackFixedColor(Color color) {
        BACK_FIXED_COLOR = color;
    }

    public void setNodeBackSelectColor(Color color) {
        BACK_SELECT_COLOR = color;
    }

    public void setNodeBackDefaultColor(Color color) {
        BACK_DEFAULT_COLOR = color;
    }

    public void setNodeBackHilightColor(Color color) {
        BACK_HILIGHT_COLOR = color;
    }

    public void setNodeBorderDragColor(Color color) {
        BORDER_DRAG_COLOR = color;
    }

    public void setNodeBorderMouseOverColor(Color color) {
        BORDER_MOUSE_OVER_COLOR = color;
    }

    public void setNodeBorderInactiveColor(Color color) {
        BORDER_INACTIVE_COLOR = color;
    }

    public void setNodeTextColor(Color color) {
        TEXT_COLOR = color;
    }

    public void setNodeTextFont(Font font) {
        TEXT_FONT = font;
    }

    public void setNodeType(int type) {
        DEFAULT_TYPE = type;
    }

    public void setMouseOverText(String[] text) {
        mouseOverText = text;
    }

    public void setPopup(String popup) {
        this.popup = popup;
    }

    public String getPopup() {
        return popup;
    }

    /**
     * Set the ID of this Node to the String <tt>id</tt>.
     */
    public void setID(String id) {
        this.id = id;
    }

    /**
     * Return the ID of this Node as a String.
     */
    public String getID() {
        return id;
    }

    /**
     * Set the location of this Node provided the Point <tt>p</tt>.
     */
    public void setLocation(Point p) {
        this.x = p.x;
        this.y = p.y;
    }

    /**
     * Return the location of this Node as a Point.
     */
    public Point getLocation() {
        return new Point((int) x, (int) y);
    }

    /**
     * Set the visibility of this Node to the boolean <tt>v</tt>.
     */
    public void setVisible(boolean v) {
        visible = v;
    }

    /**
     * Return the visibility of this Node as a boolean.
     */
    public boolean isVisible() {
        return visible;
    }

    /**
     * Set the type of this Node to the int <tt>type</tt>.
     * 
     * @see TYPE_RECTANGLE
     * @see TYPE_ROUNDRECT
     * @see TYPE_ELLIPSE
     * @see TYPE_CIRCLE
     */

    public void setType(int type) {
        typ = type;
    }

    /**
     * Return the type of this Node as an int.
     * 
     * @see TYPE_RECTANGLE
     * @see TYPE_ROUNDRECT
     * @see TYPE_ELLIPSE
     * @see TYPE_CIRCLE
     */
    public int getType() {
        return typ;
    }

    /** Set the font of this Node to the Font <tt>font</tt>. */
    public void setFont(Font font) {
        this.font = font;
    }

    /** Returns the font of this Node as a Font */
    public Font getFont() {
        return font;
    }

    /** Set the background color of this Node to the Color <tt>bgColor</tt>. */
    public void setBackColor(Color bgColor) {
        backColor = bgColor;
        backPaint = bgColor;
    }

    /** Set the background texture of this Node to the Color <tt>bgColor</tt>. */
    public void setBackPaint(Paint bgPaint) {
        backPaint = bgPaint;
    }

    /**
     * Return the background color of this Node as a Color.
     */
    public Color getBackColor() {
        return backColor;
    }

    /** Set the text color of this Node to the Color <tt>txtColor</tt>. */
    public void setTextColor(Color txtColor) {
        textColor = txtColor;
    }

    /**
     * Return the text color of this Node as a Color.
     */
    public Color getTextColor() {
        return textColor;
    }

    /** Set the label of this Node to the String <tt>label</tt>. */
    public void setLabel(String label) {
        lbl = label;
    }

    /**
     * Return the label of this Node as a String.
     */
    public String getLabel() {
        return lbl;
    }

    /** Set the fixed status of this Node to the boolean <tt>fixed</tt>. */
    public void setFixed(boolean fixed) {
        this.fixed = fixed;
    }

    /**
     * Returns true if this Node is fixed (in place).
     */
    public boolean getFixed() {
        return fixed;
    }

    // ....

    /**
     * Return the number of Edges in the cumulative Vector.
     * 
     * @deprecated this method has been replaced by the <tt>edgeCount()</tt>
     *             method.
     */
    public int edgeNum() {
        return edges.size();
    }

    /** Return the number of Edges in the cumulative Vector. */
    public int edgeCount() {
        return edges.size();
    }

    /**
     * Return an iterator over the Edges in the cumulative Vector, null if it is
     * empty.
     */
    /*
     * public Iterator getEdges() { if ( edges.size() == 0 ) return null; else
     * return edges.iterator(); }
     */

    /** Returns the local Edge count. */
    public int visibleEdgeCount() {
        return visibleEdgeCnt;
    }

    /** Add the Edge <tt>edge</tt> to the graph. */
    public void addEdge(Edge edge) {
        if (edge == null)
            return;
        edges.add(edge);
    }

    /** Remove the Edge <tt>edge</tt> from the graph. */
    public void removeEdge(Edge edge) {
        edges.remove(edge);
    }

    /** Return the width of this Node. */
    public int getWidth() {
        if (fontMetrics != null && lbl != null) {
            // return fontMetrics.stringWidth(lbl) + 12;
            return fontMetrics.stringWidth(lbl) + 35;
        } else {
            return 10;
        }
    }

    /** Return the height of this Node. */
    public int getHeight() {
        if (fontMetrics != null) {
            return fontMetrics.getHeight() + 6;
        } else {
            return 6;
        }
    }

    /** Returns true if this Node intersects Dimension <tt>d</tt>. */
    public boolean intersects(Dimension d) {
        return (drawx > 0 && drawx < d.width && drawy > 0 && drawy < d.height);
    }

    /** Returns true if this Node contains the Point <tt>px,py</tt>. */
    public boolean containsPoint(double px, double py) {
        return ((px > drawx - getWidth() / 2) && (px < drawx + getWidth() / 2)
                && (py > drawy - getHeight() / 2) && (py < drawy + getHeight()
                / 2));
    }

    /** Returns true if this Node contains the Point <tt>p</tt>. */
    public boolean containsPoint(Point p) {
        return ((p.x > drawx - getWidth() / 2)
                && (p.x < drawx + getWidth() / 2)
                && (p.y > drawy - getHeight() / 2) && (p.y < drawy
                + getHeight() / 2));
    }

    public void paintTextTag(Graphics g, TGPanel tgPanel, int tagX, int tagY,
            Color backCol, Color textCol, String[] text) {

        g.setFont(SMALL_TAG_FONT);
        
        // Compute size of text box
        FontRenderContext frc = ((Graphics2D) g).getFontRenderContext();
        int maxX = 0;
        for (String s : text) {
            TextLayout layout = new TextLayout(s, SMALL_TAG_FONT, frc);
            maxX = Math.max(maxX, (int) layout.getBounds().getWidth());
        }
        int w = maxX + 14;
        int h = text.length * 10 + 2;
        int ix = tagX + maxX/2 + 7;
        int iy = tagY;
        
        // Draw background
        g.setColor(backCol);
        g.fillRect(tagX, tagY+15, w, h);

        // Draw text
        g.setColor(textCol);
        for (int i = 0; i < text.length; i++) {
            g.drawString(text[i], tagX + 7, tagY + 25 + i * 10);
        }
    }

    /** Paints the Node. */
    public void paint(Graphics g, TGPanel tgPanel) {
        paint(g, tgPanel, false);
    }

    public void paint(Graphics g, TGPanel tgPanel, boolean mouseOver) {
        if (!intersects(tgPanel.getSize()))
            return;
        paintNodeBody(g, tgPanel);

        if (visibleEdgeCount() < edgeCount()) {
            int ix = (int) drawx;
            int iy = (int) drawy;
            int h = getHeight();
            int w = getWidth();
            int tagX = ix + (w - 7) / 2 - 2 + w % 2;
            int tagY = iy - h / 2 - 2;
            char character;
            int hiddenEdgeCount = edgeCount() - visibleEdgeCount();
            character = (hiddenEdgeCount < 9) ? (char) ('0' + hiddenEdgeCount)
                    : '*';
            paintSmallTag(g, tgPanel, tagX, tagY, Color.red, Color.white,
                    character);
        }

        if (mouseOver) {

            String[] text = mouseOverText;

            if (text != null) {
                int ix = (int) drawx;
                int iy = (int) drawy;

                int h = getHeight();
                int w = getWidth();

                int tagX = ix + (w - 7) / 2 - 2 + w % 2;
                int tagY = iy - h / 2 - 2;

                paintTextTag(g, tgPanel, tagX, tagY, Color.white, Color.black,
                        text);
            }
        }
    }

    public Color getPaintBorderColor(TGPanel tgPanel) {
        if (this == tgPanel.getDragNode())
            return BORDER_DRAG_COLOR;
        else if (this == tgPanel.getMouseOverN())
            return BORDER_MOUSE_OVER_COLOR;
        else
            return BORDER_INACTIVE_COLOR;
    }

    public Color getPaintBackColor(TGPanel tgPanel) {
        if (this == tgPanel.getSelect()) {
            return BACK_SELECT_COLOR;
        } else {
            if (fixed)
                return BACK_FIXED_COLOR;
            if (markedForRemoval)
                return BACK_MRF_COLOR;
            if (justMadeLocal)
                return BACK_JML_COLOR;
            return backColor;
        }
    }

    public Color getPaintTextColor(TGPanel tgPanel) {
        return textColor;
    }

    /** Paints the background of the node, along with its label */
    public void paintNodeBody(Graphics g, TGPanel tgPanel) {
        
        Graphics2D g2 = (Graphics2D) g;

        g.setFont(font);
        g2.setFont(g2.getFont().deriveFont(Font.BOLD));
        fontMetrics = g2.getFontMetrics();

        int ix = (int) drawx;
        int iy = (int) drawy;
        int h = getHeight();
        int w = getWidth();
        int r = h / 2 + 1; // arc radius

        Color borderCol = getPaintBorderColor(tgPanel);
        g.setColor(borderCol);

        if (typ == TYPE_ROUNDRECT) {
            g.fillRoundRect(ix - w / 2, iy - h / 2, w, h, r, r);
        } else if (typ == TYPE_ELLIPSE) {
            g.fillOval(ix - w / 2, iy - h / 2, w, h);
        } else if (typ == TYPE_CIRCLE) { // just use width for both
            // dimensions
            g.fillOval(ix - w / 2, iy - w / 2, w, w);
        } else { // TYPE_RECTANGLE
            g.fillRect(ix - w / 2, iy - h / 2, w, h);
        }

        if (backPaint == null) {

            g.setColor(Color.WHITE);
            if (typ == TYPE_ROUNDRECT) {
                g.fillRoundRect(ix - w / 2 + 2, iy - h / 2 + 2, w - 4, h - 4,
                        r, r);
            } else if (typ == TYPE_ELLIPSE) {
                g.fillOval(ix - w / 2 + 2, iy - h / 2 + 2, w - 4, h - 4);
            } else if (typ == TYPE_CIRCLE) {
                g.fillOval(ix - w / 2 + 2, iy - w / 2 + 2, w - 4, w - 4);
            } else { // TYPE_RECTANGLE
                g.fillRect(ix - w / 2 + 2, iy - h / 2 + 2, w - 4, h - 4);
            }

            Color backCol = getPaintBackColor(tgPanel);
            g.setColor(backCol);

            if (typ == TYPE_ROUNDRECT) {
                g.fillRoundRect(ix - w / 2 + 2, iy - h / 2 + 2, w - 4, h - 4,
                        r, r);
            } else if (typ == TYPE_ELLIPSE) {
                g.fillOval(ix - w / 2 + 2, iy - h / 2 + 2, w - 4, h - 4);
            } else if (typ == TYPE_CIRCLE) {
                g.fillOval(ix - w / 2 + 2, iy - w / 2 + 2, w - 4, w - 4);
            } else { // TYPE_RECTANGLE
                g.fillRect(ix - w / 2 + 2, iy - h / 2 + 2, w - 4, h - 4);
            }
        } else {
            g2.setColor(Color.WHITE);
            if (typ == TYPE_ROUNDRECT) {
                g2.fillRoundRect(ix - w / 2 + 2, iy - h / 2 + 2, w - 4, h - 4,
                        r, r);
            } else if (typ == TYPE_ELLIPSE) {
                g2.fillOval(ix - w / 2 + 2, iy - h / 2 + 2, w - 4, h - 4);
            } else if (typ == TYPE_CIRCLE) {
                g2.fillOval(ix - w / 2 + 2, iy - w / 2 + 2, w - 4, w - 4);
            } else { // TYPE_RECTANGLE
                g2.fillRect(ix - w / 2 + 2, iy - h / 2 + 2, w - 4, h - 4);
            }

            g2.setPaint(backPaint);

            if (typ == TYPE_ROUNDRECT) {
                g2.fillRoundRect(ix - w / 2 + 2, iy - h / 2 + 2, w - 4, h - 4,
                        r, r);
            } else if (typ == TYPE_ELLIPSE) {
                g2.fillOval(ix - w / 2 + 2, iy - h / 2 + 2, w - 4, h - 4);
            } else if (typ == TYPE_CIRCLE) {
                g2.fillOval(ix - w / 2 + 2, iy - w / 2 + 2, w - 4, w - 4);
            } else { // TYPE_RECTANGLE
                g2.fillRect(ix - w / 2 + 2, iy - h / 2 + 2, w - 4, h - 4);
            }

        }

        // if (backPaint != null && backPaint instanceof TexturePaint) {

        // Big hack to draw an outlined font...

        // g2.setFont(g2.getFont().deriveFont(Font.BOLD));
        if (antiAlias) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
        } else {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_OFF);
        }

        g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);

        int len = fontMetrics.stringWidth(lbl);
        int descent = fontMetrics.getDescent() + 1;
/*
        g2.setColor(Color.BLACK);
        
        g2.drawString(lbl, ix - len / 2, iy
                + descent + 1);

        g2.drawString(lbl, ix - len / 2, iy
                + descent - 1);

        g2.drawString(lbl, ix - len / 2 + 1, iy
                + descent + 1);

        g2.drawString(lbl, ix - len / 2 + 1, iy
                + descent - 1);

        g2.drawString(lbl, ix - len / 2 - 1, iy
                + descent + 1);

        g2.drawString(lbl, ix - len / 2 - 1, iy
                + descent - 1);

        g2.drawString(lbl, ix - len / 2 - 1, iy
                + descent + 0);

        g2.drawString(lbl, ix - len / 2 + 1, iy
                + descent + 0);

        g2.setColor(Color.WHITE);
        */
        
        g2.setColor(TEXT_COLOR);
        
        g2.drawString(lbl, ix - len / 2, iy
                + descent);
        /*
         * } else { Color textCol = getPaintTextColor(tgPanel);
         * g2.setColor(textCol); g2.drawString(lbl, ix -
         * fontMetrics.stringWidth(lbl)/2, iy + fontMetrics.getDescent() +1); }
         */
    }

    /** Paints a tag with containing a character in a small font. */
    public void paintSmallTag(Graphics g, TGPanel tgPanel, int tagX, int tagY,
            Color backCol, Color textCol, char character) {
        g.setColor(backCol);
        g.fillRect(tagX, tagY, 8, 8);
        g.setColor(textCol);
        g.setFont(SMALL_TAG_FONT);
        g.drawString("" + character, tagX + 2, tagY + 7);
    }

    // Modifications by Lutz Dornbusch
    public String getURL() {
        return strUrl;
    }

    public void setURL(String strUrl) {
        this.strUrl = strUrl;
    }
} // end com.touchgraph.graphlayout.Node
