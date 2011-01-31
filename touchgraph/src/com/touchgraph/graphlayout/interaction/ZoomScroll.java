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

package com.touchgraph.graphlayout.interaction;

import java.awt.Dimension;

import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.touchgraph.graphlayout.GraphListener;
import com.touchgraph.graphlayout.TGAbstractLens;
import com.touchgraph.graphlayout.TGPanel;
import com.touchgraph.graphlayout.TGPoint2D;

// import javax.swing.*;

/**
 * ZoomScroll: Contains code for enlarging the graph by zooming in.
 * 
 * @author Alexander Shapiro
 * @version 1.21 $Id: ZoomScroll.java,v 1.2 2002/09/23 18:45:48 ldornbusch Exp $
 */
public class ZoomScroll implements GraphListener {

    private static final int MAX = 19;
    
    private static final int MIN = -31;
    
    private static final int INITIAL = -4;
    
    protected ZoomLens zoomLens;

    private JSlider zoomSlider;

    private TGPanel tgPanel;
    
    private double zoomValue = INITIAL;

    // ............

    /**
     * Constructor with TGPanel <tt>tgp</tt>.
     */
    
    public ZoomScroll(TGPanel tgp) {
	this(tgp, false);
    }
    
    public ZoomScroll(TGPanel tgp, boolean auto) {
        tgPanel = tgp;
        if (! auto) {
            zoomSlider = new JSlider(JSlider.VERTICAL, MIN, MAX, INITIAL);
            zoomSlider.addChangeListener(new ZoomChangeListener());
        }
        zoomLens = new ZoomLens();
        tgPanel.addGraphListener(this);
    }

    public JSlider getZoomSlider() {
        return zoomSlider;
    }

    public ZoomLens getLens() {
        return zoomLens;
    }

    public void graphMoved() {
    } // From GraphListener interface

    public void graphReset() {
	if (zoomSlider != null) {
	    zoomSlider.setValue(-10);
	} else {
	    zoomValue = -10;
	}
    } // From GraphListener interface

    public int getZoomValue() {
	if (zoomSlider != null) {
	    double orientedValue = zoomSlider.getValue() - zoomSlider.getMinimum();
	    double range = zoomSlider.getMaximum() - zoomSlider.getMinimum();
	    return (int) ((orientedValue / range) * 200 - 100);
	}
	return (int) (((zoomValue - MIN)/(MAX - MIN)) * 200) - 100;
    }

    public void setZoomValue(int value) {
	if (zoomSlider != null) {
	    double range = zoomSlider.getMaximum() - zoomSlider.getMinimum();
	    zoomSlider.setValue((int) ((value + 100) / 200.0 * range + 0.5)
		    + zoomSlider.getMinimum());
	}
	zoomValue = (int) ((value + 100) / 200.0 * (MAX - MIN) + .5) + MIN;
    }

    private class ZoomChangeListener implements ChangeListener {

        public void stateChanged(ChangeEvent e) {
            tgPanel.repaintAfterMove();
        }
    }

    class ZoomLens extends TGAbstractLens {

	private int shiftx = 0;
	private int shifty = 0;
	
	protected void computeLens() {
	    if (zoomSlider == null) {
		Dimension d = tgPanel.getSize();
		TGPoint2D tl = tgPanel.getTopLeft();
		TGPoint2D br = tgPanel.getBottomRight();

		int maxx = (int) br.x;
		int minx = (int) tl.x;
		int maxy = (int) br.y;
		int miny = (int) tl.y;
		double rangex = maxx - minx;
		double rangey = maxy - miny;
		double requiredZoom = rangex / (d.width-20);
		if (rangey / d.height > requiredZoom) {
		    requiredZoom = rangey / (d.height-20);
		}
		if (requiredZoom <= 1) {
		    requiredZoom = 1;
		}
		shiftx = (maxx + minx) / 2;
		shifty = (maxy + miny) / 2;
		/*
		System.out.println("Dimension = " + d.width + "x" + d.height);
		System.out.println("Top left = (" + tl.x + "," + tl.y + ")");
		System.out.println("Bottom right = (" + br.x + "," + br.y + ")");
		System.out.println("shiftx = " + shiftx + ", shifty = " + shifty);
		System.out.println("required zoom = " + requiredZoom);
		*/
		
		// Math.pow(2, zoomValue/10.0) = 1/requiredZoom
		// zoomValue/10.0 = 2log(1/requiredZoom)
		// zoomValue = 10.0 * 2Log(1/requiredZoom)
		zoomValue = 10.0 * Math.log(1.0/requiredZoom)/Math.log(2.0);
		if (zoomValue < MIN) {
		    zoomValue = MIN;
		}
		if (zoomValue > MAX) {
		    zoomValue = MAX;
		}
		// System.out.println("zoomValue = " + zoomValue);
	    }
	}

        protected void applyLens(TGPoint2D p) {
            double val = zoomSlider != null ? zoomSlider.getValue() : zoomValue;
//            if (zoomSlider == null) {
//        	System.out.println("applyLens: point was " + p);
//            }
            p.x = (p.x - shiftx) * Math.pow(2, val / 10.0);
            p.y = (p.y - shifty) * Math.pow(2, val / 10.0);
//            if (zoomSlider == null) {
//        	System.out.println("applyLens: point becomes " + p);
//            }
        }

        protected void undoLens(TGPoint2D p) {
            double val = zoomSlider != null ? zoomSlider.getValue() : zoomValue;
            p.x = p.x / Math.pow(2, val / 10.0) + shiftx;
            p.y = p.y / Math.pow(2, val / 10.0) + shifty;
        }
    }

} // end com.touchgraph.graphlayout.interaction.ZoomScroll
