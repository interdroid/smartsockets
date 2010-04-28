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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;

/**
 * Edge.
 * 
 * @author Alexander Shapiro
 * @version 1.22-jre1.1 $Id: Edge.java,v 1.1 2002/09/19 15:58:07 ldornbusch Exp $
 */
public class Edge {

    public static Color DEFAULT_COLOR = Color.decode("#006090");

    public static Color MOUSE_OVER_COLOR = Color.decode("#ccddff");

    public static int DEFAULT_LENGTH = 40;

    public Node from; // Should be private, changing from effects "from" Node

    public Node to; // Should be private, changing from effects "to" Node

    protected boolean arrowHead = false;

    protected Color col;

    protected int length;

    protected boolean visible;

    protected String id = null;

    private String[] mouseOverText = null;

    // ............

    /**
     * Constructor with two Nodes and a length.
     */
    public Edge(Node f, Node t, int len) {
        from = f;
        to = t;
        length = len;
        col = DEFAULT_COLOR;
        visible = false;
    }

    /**
     * Constructor with two Nodes, which uses a default length.
     */
    public Edge(Node f, Node t) {
        this(f, t, DEFAULT_LENGTH);
    }

    // setters and getters ...............

    public static void setEdgeDefaultColor(Color color) {
        DEFAULT_COLOR = color;
    }

    public static void setEdgeMouseOverColor(Color color) {
        MOUSE_OVER_COLOR = color;
    }

    public static void setEdgeDefaultLength(int length) {
        DEFAULT_LENGTH = length;
    }

    /** Returns the starting "from" node of this edge as Node. */
    public Node getFrom() {
        return from;
    }

    /** Returns the terminating "to" node of this edge as Node. */
    public Node getTo() {
        return to;
    }

    /** Returns the color of this edge as Color. */
    public Color getColor() {
        return col;
    }

    /** Set the color of this Edge to the Color <tt>color</tt>. */
    public void setColor(Color color) {
        col = color;
    }

    public void useArrowHead(boolean value) {
        arrowHead = value;
    }

    /** Returns the ID of this Edge as a String. */
    public String getID() {
        return id;
    }

    /** Set the ID of this Edge to the String <tt>id</tt>. */
    public void setID(String id) {
        this.id = id;
    }

    /** Returns the length of this Edge as a double. */
    public int getLength() {
        return length;
    }

    /** Set the length of this Edge to the int <tt>len</tt>. */
    public void setLength(int len) {
        length = len;
    }

    /** Set the visibility of this Edge to the boolean <tt>v</tt>. */
    public void setVisible(boolean v) {
        visible = v;
    }

    /** Return the visibility of this Edge as a boolean. */
    public boolean isVisible() {
        return visible;
    }

    public Node getOtherEndpt(Node n) { // yields false results if Node n is not
                                        // an endpoint
        if (to != n)
            return to;
        else
            return from;
    }

    /** Switches the endpoints of the edge */
    public void reverse() {
        Node temp = to;
        to = from;
        from = temp;
    }

    public boolean intersects(Dimension d) {
        int x1 = (int) from.drawx;
        int y1 = (int) from.drawy;
        int x2 = (int) to.drawx;
        int y2 = (int) to.drawy;

        return (((x1 > 0 || x2 > 0) && (x1 < d.width || x2 < d.width)) && ((y1 > 0 || y2 > 0) && (y1 < d.height || y2 < d.height)));

    }

    public double distFromPoint(double px, double py) {
        double x1 = from.drawx;
        double y1 = from.drawy;
        double x2 = to.drawx;
        double y2 = to.drawy;

        if (px < Math.min(x1, x2) - 8 || px > Math.max(x1, x2) + 8
                || py < Math.min(y1, y2) - 8 || py > Math.max(y1, y2) + 8)
            return 1000;

        double dist = 1000;
        if (x1 - x2 != 0)
            dist = Math.abs((y2 - y1) / (x2 - x1) * (px - x1) + (y1 - py));
        if (y1 - y2 != 0)
            dist = Math.min(dist, Math.abs((x2 - x1) / (y2 - y1) * (py - y1)
                    + (x1 - px)));

        return dist;
    }

    public boolean containsPoint(double px, double py) {
        return distFromPoint(px, py) < 10;
    }

    public static void drawArrowHead(Graphics g, double xCenter,
            double yCenter, double x, double y, double stroke) {

        double aDir = Math.atan2(xCenter - x, yCenter - y);
        // g.drawLine(x,y,xCenter,yCenter);

        Polygon tmpPoly = new Polygon();
        double i1 = 6 + stroke * 2;
        double i2 = 6 + stroke; // make the arrow head the same size regardless
                                // of the length length
        tmpPoly.addPoint((int) Math.round(x), (int) Math.round(y)); // arrow tip
        tmpPoly.addPoint((int) Math.round(x + xCor(i1, aDir + 0.5)), (int) Math
                .round(y + yCor(i1, aDir + 0.5)));
        // tmpPoly.addPoint(x+xCor(i2,aDir),y+yCor(i2,aDir));
        tmpPoly.addPoint((int) Math.round(x + xCor(i1, aDir - 0.5)), (int) Math
                .round(y + yCor(i1, aDir - 0.5)));
        tmpPoly.addPoint((int) Math.round(x), (int) Math.round(y)); // arrow tip
        // arrow tip
        g.drawPolygon(tmpPoly);
        g.fillPolygon(tmpPoly); // remove this line to leave arrow head
                                // unpainted

        g.drawLine((int) Math.round(xCenter), (int) Math.round(yCenter),
                (int) Math.round(x + xCor(i2, aDir)), (int) Math.round(y
                        + yCor(i2, aDir)));
    }

    private static double yCor(double len, double dir) {
        return (len * Math.cos(dir));
    }

    private static double xCor(double len, double dir) {
        return (len * Math.sin(dir));
    }

    public static void paintArrow(Graphics g, int x1, int y1, int x2, int y2,
            Color c) {
        System.out.println("EEK");
        paintArrow((Graphics2D) g, x1, y1, x2, y2, false, c);
    }

    public static void paintArrow(Graphics2D g, int x1, int y1, int x2, int y2,
            boolean head, Color c) {

        // Forget hyperbolic bending for now

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(c);

        /*
         * int x3=x1; int y3=y1;
         * 
         * double dist=Math.sqrt((x2-x1)*(x2-x1) + (y2-y1)*(y2-y1)); if
         * (dist>10) { double adjustDistRatio = (dist-10)/dist; x3=(int)
         * (x1+(x2-x1)*adjustDistRatio); y3=(int) (y1+(y2-y1)*adjustDistRatio); }
         * 
         * x3=(int) ((x3*4+x1)/5.0); y3=(int) ((y3*4+y1)/5.0);
         * 
         * g.drawLine(x3, y3, x2, y2);
         * 
         * g.drawLine(x1, y1, x3, y3); g.drawLine(x1+1, y1, x3, y3);
         * g.drawLine(x1+2, y1, x3, y3); g.drawLine(x1+3, y1, x3, y3);
         * g.drawLine(x1+4, y1, x3, y3); g.drawLine(x1-1, y1, x3, y3);
         * g.drawLine(x1-2, y1, x3, y3); g.drawLine(x1-3, y1, x3, y3);
         * g.drawLine(x1-4, y1, x3, y3);
         * 
         * g.drawLine(x1, y1+1, x3, y3); g.drawLine(x1, y1+2, x3, y3);
         * g.drawLine(x1, y1+3, x3, y3); g.drawLine(x1, y1+4, x3, y3);
         * g.drawLine(x1, y1-1, x3, y3); g.drawLine(x1, y1-2, x3, y3);
         * g.drawLine(x1, y1-3, x3, y3); g.drawLine(x1, y1-4, x3, y3);
         */

        g.setStroke(new BasicStroke((float) 2.0));

        g.drawLine(x1, y1, x2, y2);
        
        if (head) {
            double x3 = x1;
            double y3 = y1;

            // double x4 = x1;
            // double y4 = y1;
            
            int x3rounded = x1;
            int y3rounded = y1;

            double dist = Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1)
                    * (y2 - y1));

            if (dist > 10) {
                double adjustDistRatio = (dist - 10) / dist;
                x3 = (x1 + (x2 - x1) * adjustDistRatio);
                y3 = (y1 + (y2 - y1) * adjustDistRatio);

                // x4 = (int) (x1 + (x2 - x1) * adjustDistRatio);
                // y4 = (int) (y1 + (y2 - y1) * adjustDistRatio);
            

                x3 = ((x3 * 4.0 + x1) / 5.0);
                y3 = ((y3 * 4.0 + y1) / 5.0);

                // x4 = ((x3 * 9.0 + x1 * 2.0) / 11.0);
                // y4 = ((y3 * 9.0 + y1 * 2.0) / 11.0);
                
                x3rounded = (int) Math.round(x3);
                y3rounded = (int) Math.round(y3);
            }

            // g.drawLine(x2, y2, x3rounded, y3rounded);

            // double aDir = Math.atan2(x4 - x3, y4 - y3);
            double aDir = Math.atan2(x2 - x1, y2 - y1);
            double stroke = 1.0;

            // make the arrow head the same size regardless of the length
            double i1 = 6 + stroke * 2;
            // double i2 = 6 + stroke;

            Polygon tmpPoly = new Polygon();
            tmpPoly.addPoint(x3rounded, y3rounded);
            
            double x4 = x3 - xCor(i1, aDir);
            double y4 = y3 - yCor(i1, aDir);
            double xdiff = (x3 - x4) / 2.0;
            double ydiff = (y3 - y4) / 2.0;
            
            tmpPoly.addPoint((int) Math.round(x4 + ydiff), (int) Math.round(y4 - xdiff));
            tmpPoly.addPoint((int) Math.round(x4 - ydiff), (int) Math.round(y4 + xdiff));

            /*
            tmpPoly.addPoint((int) Math.round(x3 + xCor(i1, aDir + 0.5)),
                    (int) Math.round(y3 + yCor(i1, aDir + 0.5)));

            tmpPoly.addPoint((int) Math.round(x3 + xCor(i1, aDir - 0.5)),
                    (int) Math.round(y3 + yCor(i1, aDir - 0.5)));
*/

            tmpPoly.addPoint(x3rounded, y3rounded);

            g.drawPolygon(tmpPoly);
            g.fillPolygon(tmpPoly);

            // g.drawLine(x1, y1, (int) Math.round(x3 + xCor(i2, aDir)),
            //        (int) Math.round(y3 + yCor(i2, aDir)));
        }
 
    }
    
    public void setMouseOverText(String[] text) {
        mouseOverText  = text;
    }

    public void paint(Graphics g, TGPanel tgPanel) {
        Color c = (tgPanel.getMouseOverE() == this) ? MOUSE_OVER_COLOR : col;

        int x1 = (int) from.drawx;
        int y1 = (int) from.drawy;
        int x2 = (int) to.drawx;
        int y2 = (int) to.drawy;

        if (intersects(tgPanel.getSize())) {
            paintArrow((Graphics2D) g, x1, y1, x2, y2, arrowHead, c);
            if (tgPanel.getMouseOverE() == this) {

                String[] text = mouseOverText;

                if (text != null) {
                    Point mouse = tgPanel.getMousePos();

                    paintTextTag(g, tgPanel, mouse.x, mouse.y, Color.white, Color.black,
                            text);
                }
            }
        }
    }
    
    public void paintTextTag(Graphics g, TGPanel tgPanel, int tagX, int tagY,
            Color backCol, Color textCol, String[] text) {

        g.setFont(Node.SMALL_TAG_FONT);
        
        // Compute size of text box
        FontRenderContext frc = ((Graphics2D) g).getFontRenderContext();
        int maxX = 0;
        for (String s : text) {
            TextLayout layout = new TextLayout(s, Node.SMALL_TAG_FONT, frc);
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

} // end com.touchgraph.graphlayout.Edge
