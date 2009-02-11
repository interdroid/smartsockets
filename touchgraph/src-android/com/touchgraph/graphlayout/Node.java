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

import java.util.Vector;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.Typeface;

/**
 * Node.
 * 
 * @author Alexander Shapiro
 * @author Murray Altheim (2001-11-06; added support for round rects and
 *         alternate Node colors)
 * @version 1.22-jre1.1 $Id: Node.java,v 1.2 2002/09/20 14:00:22 ldornbusch Exp $
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

    // Variables that store default values for colors + fonts + node type
    public int BACK_FIXED_COLOR = Color.argb(255, 255, 32, 20);

    public int BACK_SELECT_COLOR = Color.argb(255, 225, 164, 0);

    public int BACK_COLOR = Color.parseColor("#4080A0");

    public int BACK_HILIGHT_COLOR = Color.argb(255, 205, 192, 166);

    public int BACK_MRF_COLOR = Color.argb(255, 2, 35, 81);

    public int BACK_JML_COLOR = Color.argb(255, 58, 176, 255);

    public int BORDER_DRAG_COLOR = Color.argb(255, 130, 130, 180);

    public int BORDER_MOUSE_OVER_COLOR = Color.argb(255, 160, 160, 180);

    public int BORDER_COLOR = Color.argb(255, 30, 50, 160);

    public int TEXT_COLOR = Color.BLACK;

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

    protected String lbl;

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

    private Vector<Edge> edges;

    private String[] mouseOverText;

    private String popup;

    // ............

    // Modification by Lutz
    private String strUrl;

    /**
     * Minimal constructor which will generate an ID value from Java's Date
     * class. Defaults will be used for type and color. The label will be taken
     * from the ID value.
     */
    public Node() {
        initialize(null);
        lbl = id;
    }

    /**
     * Constructor with the required ID <tt>id</tt>, using defaults for type
     * (rectangle), color (a static variable from TGPanel). The Node's label
     * will be taken from the ID value.
     */
    public Node(String id) {
        initialize(id);
        lbl = id;
    }

    /**
     * Constructor with Strings for ID <tt>id</tt> and <tt>label</tt>,
     * using defaults for type (rectangle) and color (a static variable from
     * TGPanel). If the label is null, it will be taken from the ID value.
     */
    public Node(String id, String label) {
        initialize(id);
        if (label == null)
            lbl = id;
        else
            lbl = label;
    }

    /**
     * Constructor with a String ID <tt>id</tt>, an int <tt>type</tt>,
     * Background Color <tt>bgColor</tt>, and a String <tt>label</tt>. If
     * the label is null, it will be taken from the ID value.
     * 
     * @see TYPE_RECTANGLE
     * @see TYPE_ROUNDRECT
     */
    public Node(String id, int type, int color, String label) {
        initialize(id);
        typ = type;
        BACK_COLOR = color;
        if (label == null)
            lbl = id;
        else
            lbl = label;
    }

    private void initialize(String identifier) {
        this.id = identifier;
        edges = new Vector<Edge>();
        x = Math.random() * 2 - 1; // If multiple nodes are added without
        // repositioning,
        y = Math.random() * 2 - 1; // randomizing starting location causes them
        // to spread out nicely.
        repulsion = 100;
        fixed = false;
        typ = DEFAULT_TYPE;
        visibleEdgeCnt = 0;
        visible = false;
    }

    // setters and getters ...............

    public void setNodeBackFixedColor(int color) {
        BACK_FIXED_COLOR = color;
    }

    public void setNodeBackSelectColor(int color) {
        BACK_SELECT_COLOR = color;
    }

    public void setNodeBackDefaultColor(int color) {
        BACK_COLOR = color;
    }

    public void setNodeBackHilightColor(int color) {
        BACK_HILIGHT_COLOR = color;
    }

    public void setNodeBorderDragColor(int color) {
        BORDER_DRAG_COLOR = color;
    }

    public void setNodeBorderMouseOverColor(int color) {
        BORDER_MOUSE_OVER_COLOR = color;
    }

    public void setNodeBorderColor(int color) {
        BORDER_COLOR = color;
    }

    public void setNodeTextColor(int color) {
        TEXT_COLOR = color;
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

    /** Set the background color of this Node to the Color <tt>bgColor</tt>. */
    public void setBackColor(int bgColor) {
        BACK_COLOR = bgColor;
    }

    /**
     * Return the background color of this Node as a Color.
     */
    public int getBackColor() {
        return BACK_COLOR;
    }

    /** Set the text color of this Node to the Color <tt>txtColor</tt>. */
    public void setTextColor(int txtColor) {
        TEXT_COLOR = txtColor;
    }

    /**
     * Return the text color of this Node as a Color.
     */
    public int getTextColor() {
        return TEXT_COLOR;
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

    /** Return the Edge at int <tt>index</tt>. */
    public Edge edgeAt(int index) {
        return (Edge) edges.elementAt(index);
    }

    /** Add the Edge <tt>edge</tt> to the graph. */
    public void addEdge(Edge edge) {
        if (edge == null)
            return;
        edges.addElement(edge);
    }

    /** Remove the Edge <tt>edge</tt> from the graph. */
    public void removeEdge(Edge edge) {
        edges.removeElement(edge);
    }

    /** Return the width of this Node. */
    public int getWidth() {
        return 25;
    }

    /** Return the height of this Node. */
    public int getHeight() {
        return 20;
    }

    /** Returns true if this Node intersects Dimension <tt>d</tt>. */
    public boolean intersects(int width, int height) {
        return (drawx > 0 && drawx < width && drawy > 0 && drawy < height);
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

    public void paintTextTag(Canvas canvas, TGPanel tgPanel, int tagX,
            int tagY, int backCol, int textCol, String[] text) {
        Paint paint = new Paint();
        paint.setColor(textCol);
        // paint.setTypeface(typeface)

        for (int i = 0; i < text.length; i++) {
            canvas.drawText(text[i], tagX + 7, tagY + 25 + i * 10, paint);
        }
    }

    /** Paints the Node. */
    public void paint(Canvas canvas, TGPanel tgPanel) {
        paint(canvas, tgPanel, false);
    }

    public void paint(Canvas canvas, TGPanel tgPanel, boolean mouseOver) {
        if (!intersects(tgPanel.getWidth(), tgPanel.getHeight()))
            return;
        paintNodeBody(canvas, tgPanel);

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
            paintSmallTag(canvas, tgPanel, tagX, tagY, Color.GREEN, Color.BLUE,
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

                paintTextTag(canvas, tgPanel, tagX, tagY, Color.WHITE,
                        Color.BLACK, text);
            }
        }
    }

    /** Paints the background of the node, along with its label */
    public void paintNodeBody(Canvas canvas, TGPanel tgPanel) {

        int ix = (int) drawx;
        int iy = (int) drawy;
        int h = getHeight();
        int w = getWidth();
        int r = h / 2 + 1; // arc radius

        // node's border color
        Paint paint = new Paint();
        paint.setColor(BORDER_COLOR);
        paint.setAntiAlias(true);

        if (typ == TYPE_ROUNDRECT) {
            canvas.drawRoundRect(new RectF(ix - w / 2, iy - h / 2, (ix - w / 2)
                    + w, (iy - h / 2) + h), r, r, paint);
        } else if (typ == TYPE_ELLIPSE) {
            canvas.drawOval(new RectF(ix - w / 2, iy - h / 2, (ix - w / 2) + w,
                    (iy - h / 2) + h), paint);
        } else if (typ == TYPE_CIRCLE) { // just use width for both
            // dimensions
            canvas.drawCircle(ix, iy, w / 2, paint);
        } else { // TYPE_RECTANGLE
            canvas.drawRect(ix - w / 2, iy - h / 2, (ix - w / 2) + w,
                    (iy - h / 2) + h, paint);
        }

        // node's fill color
        paint.setColor(BACK_COLOR);

        if (typ == TYPE_ROUNDRECT) {
            canvas.drawRoundRect(new RectF(ix - w / 2 + 2, iy - h / 2 + 2, (ix
                    - w / 2 + 2)
                    + w - 4, (iy - h / 2 + 2) + h - 4), r, r, paint);
        } else if (typ == TYPE_ELLIPSE) {
            canvas.drawOval(new RectF(ix - w / 2 + 2, iy - h / 2 + 2, (ix - w
                    / 2 + 2)
                    + w - 4, (iy - h / 2 + 2) + h - 4), paint);
        } else if (typ == TYPE_CIRCLE) {
            canvas.drawCircle(ix, iy, (w - 4) / 2, paint);
        } else { // TYPE_RECTANGLE
            canvas.drawRect(ix - w / 2 + 2, iy - h / 2 + 2, (ix - w / 2 + 2)
                    + w - 4, (iy - h / 2 + 2) + h - 4, paint);
        }

        float textWidth = paint.measureText(lbl);
        paint.setColor(TEXT_COLOR);
        canvas.drawText(lbl, ix - (textWidth / 2), iy + 4, paint);
    }

    /** Paints a tag with containing a character in a small font. */
    public void paintSmallTag(Canvas canvas, TGPanel tgPanel, int tagX,
            int tagY, int backCol, int textCol, char character) {
        Paint paint = new Paint();
        paint.setColor(backCol);
        canvas.drawRect(new RectF(tagX, tagY, tagX + 12, tagY + 12), paint);
        paint.setColor(textCol);
        // canvas.drawText("" + character, tagX + 2, tagY + 7, paint);
        canvas.drawText("" + character, tagX + 2, tagY + 10, paint);
    }

    // Modifications by Lutz Dornbusch
    public String getURL() {
        return strUrl;
    }

    public void setURL(String strUrl) {
        this.strUrl = strUrl;
    }
} // end com.touchgraph.graphlayout.Node
