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

import java.util.ArrayList;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.touchgraph.graphlayout.graphelements.TGForEachNode;

/**
 * GLPanel contains code for adding scrollbars and interfaces to the TGPanel The
 * "GL" prefix indicates that this class is GraphLayout specific, and will
 * probably need to be rewritten for other applications.
 * 
 * @author Alexander Shapiro
 * @version 1.22-jre1.1 $Id: GLPanel.java,v 1.3 2002/09/23 18:45:56 ldornbusch
 *          Exp $
 */
public class GLPanel extends ViewGroup {

    public String zoomLabel = "Zoom"; // label for zoom menu item

    public String rotateLabel = "Rotate"; // label for rotate menu item

    private int zoom = 0;

    private double rotateAngle = 0;

    private final Button zoomOutButton;

    private final Button zoomInButton;

    private final Button rotateLeftButton;

    private final Button rotateRightButton;

    public Node popupNode;

    public Edge popupEdge;

    protected TGPanel tgPanel;

    protected TGLensSet tgLensSet;

    // ............

    /**
     * Default constructor.
     */
    public GLPanel(Context context) {
        super(context);
        setBackgroundColor(Color.RED);
        tgLensSet = new TGLensSet();
        tgPanel = new TGPanel(context);

        ArrayList<View> touchables = new ArrayList<View>();
        touchables.add(this);
        addTouchables(touchables);

        rotateLeftButton = new Button(context);
        rotateLeftButton.setText("<");

        rotateLeftButton.setOnTouchListener(new View.OnTouchListener() {

            public boolean onTouch(View view, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_MOVE
                        || event.getAction() == MotionEvent.ACTION_DOWN) {
                    rotateAngle -= 0.05;
                    tgPanel.repaintAfterMove();
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    rotateLeftButton.setSelected(false);
                }
                return false;
            }

        });
        rotateRightButton = new Button(context);
        rotateRightButton.setText(">");
        rotateRightButton.setOnTouchListener(new View.OnTouchListener() {

            public boolean onTouch(View view, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_MOVE
                        || event.getAction() == MotionEvent.ACTION_DOWN) {
                    rotateAngle += 0.05;
                    tgPanel.repaintAfterMove();
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    rotateRightButton.setSelected(false);
                }
                return false;
            }

        });
        zoomInButton = new Button(context);
        zoomInButton.setText("+");
        zoomInButton.setOnTouchListener(new View.OnTouchListener() {

            public boolean onTouch(View view, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_MOVE
                        || event.getAction() == MotionEvent.ACTION_DOWN) {
                    zoom++;
                    tgPanel.repaintAfterMove();
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    zoomInButton.setSelected(false);
                }
                return false;
            }

        });
        zoomOutButton = new Button(context);
        zoomOutButton.setText("-");
        zoomOutButton.setOnTouchListener(new View.OnTouchListener() {

            public boolean onTouch(View view, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_MOVE
                        || event.getAction() == MotionEvent.ACTION_DOWN) {
                    zoom--;
                    tgPanel.repaintAfterMove();
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    zoomOutButton.setSelected(false);
                }
                return false;
            }

        });
        initialize();

    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        System.out.println(event);
        return super.onTouchEvent(event);
    }

    /**
     * Initialize panel, lens, and establish a random graph as a demonstration.
     */
    public void initialize() {
        buildPanel();
        buildLens();
        tgPanel.setLensSet(tgLensSet);
        // tgPanel.addNode(); //Add a starting node.
        /*
         * try { randomGraph(); } catch ( TGException tge ) {
         * System.err.println(tge.getMessage());
         * tge.printStackTrace(System.err); }
         */
    }

    /** Return the TGPanel used with this GLPanel. */
    public TGPanel getTGPanel() {
        return tgPanel;
    }

    // navigation .................

    // rotation ...................

    // zoom .......................

    // ....

    public void buildLens() {
        tgLensSet.addLens(new ZoomLens());
        tgLensSet.addLens(new RotateLens());
        tgLensSet.addLens(tgPanel.getAdjustOriginLens());

    }

    class ZoomLens extends TGAbstractLens {
        protected void applyLens(TGPoint2D p) {
            p.x = p.x * Math.pow(2, zoom / 10.0);
            p.y = p.y * Math.pow(2, zoom / 10.0);

        }

        protected void undoLens(TGPoint2D p) {
            p.x = p.x / Math.pow(2, zoom / 10.0);
            p.y = p.y / Math.pow(2, zoom / 10.0);
        }
    }

    class RotateLens extends TGAbstractLens {
        double computeAngle(double x, double y) {
            double angle = Math.atan(y / x);

            if (x == 0) // There is probably a better way of hangling boundary
                // conditions, but whatever
                if (y > 0)
                    angle = Math.PI / 2;
                else
                    angle = -Math.PI / 2;

            if (x < 0)
                angle += Math.PI;

            return angle;
        }

        protected void applyLens(TGPoint2D p) {
            double currentAngle = computeAngle(p.x, p.y);
            double dist = Math.sqrt((p.x * p.x) + (p.y * p.y));

            p.x = dist * Math.cos(currentAngle + rotateAngle);
            p.y = dist * Math.sin(currentAngle + rotateAngle);
        }

        protected void undoLens(TGPoint2D p) {
            double currentAngle = computeAngle(p.x, p.y);
            double dist = Math.sqrt((p.x * p.x) + (p.y * p.y));

            p.x = dist * Math.cos(currentAngle - rotateAngle);
            p.y = dist * Math.sin(currentAngle - rotateAngle);
        }
    }

    public void buildPanel() {
        addView(rotateLeftButton);
        addView(rotateRightButton);
        addView(zoomInButton);
        addView(zoomOutButton);
        addView(tgPanel);
    }

    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        tgPanel.update(canvas);
    }

    public void randomGraph() throws TGException {
        // return;
        Node n1 = tgPanel.addNode();
        n1.setType(0);
        for (int i = 0; i < 20; i++) {
            tgPanel.addNode();
        }

        TGForEachNode fen = new TGForEachNode() {
            public void forEachNode(Node n) {
                for (int i = 0; i < 2; i++) {
                    Node r = tgPanel.getGES().getRandomNode();
                    if (r != n && tgPanel.findEdge(r, n) == null)
                        tgPanel.addEdge(r, n, Edge.DEFAULT_LENGTH);
                }
            }
        };
        tgPanel.getGES().forAllNodes(fen);

        tgPanel.setLocale(n1, 1);
        tgPanel.setSelect(n1);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        // TODO Auto-generated method stub
        tgPanel.layout(l, t, r, b);
        zoomInButton.layout(l, t, l + 40, t + 40);
        zoomOutButton.layout(l + 40, t, l + 80, t + 40);
        rotateLeftButton.layout(r - 80, t, r - 40, t + 40);
        rotateRightButton.layout(r - 40, t, r, t + 40);

    }
} // end com.touchgraph.graphlayout.GLPanel
