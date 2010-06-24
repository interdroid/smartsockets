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

package com.touchgraph.graphlayout.graphelements;

import com.touchgraph.graphlayout.Node;
import com.touchgraph.graphlayout.Edge;
import com.touchgraph.graphlayout.NodePair;
import com.touchgraph.graphlayout.TGException;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Vector;
import java.util.Hashtable;
import java.util.Enumeration;

/**
 * GraphEltSet contains data about the graph's components. Currently the only
 * components are edges and nodes.
 * 
 * @author Alexander Shapiro
 * @author Murray Altheim (added support for IDs)
 * @version 1.22-jre1.1 $Id: GraphEltSet.java,v 1.2 2002/09/20 14:03:29
 *          ldornbusch Exp $
 */
public class GraphEltSet implements ImmutableGraphEltSet {

    protected Vector<Node> nodes;

    protected Collection<Edge> edges;

    /**
     * The Hashtable containing references to the Node IDs of the current graph.
     */
    protected Hashtable<String, Node> nodeIDRegistry = null;

    // ...........

    /** Default constructor. */
    public GraphEltSet() {
        nodes = new Vector<Node>();
        edges = Collections.synchronizedSet(new HashSet<Edge>());
        nodeIDRegistry = new Hashtable<String, Node>(); // registry of Node IDs
    }
    
    // ..........
    
    private static class NodeIterator implements Iterator<Node> {
        
        Node[] nodes;
        int i = 0;
        
        NodeIterator(Node[] nodes) {
            this.nodes = nodes;
        }

        public boolean hasNext() {
            return i < nodes.length;
        }

        public Node next() {
            if (i >= nodes.length) {
                throw new NoSuchElementException("Iterator exhausted");
            }
            return nodes[i++];
        }

        public void remove() {
            throw new UnsupportedOperationException("remove not supported");
        }
    }
    
    private static class NodeIterable implements Iterable<Node> {
        Node[] nodes;
        NodeIterable(Vector<Node> nodes) {
            this.nodes = nodes.toArray(new Node[nodes.size()]);
            Arrays.sort(this.nodes);
        }

        public Iterator<Node> iterator() {
            return new NodeIterator(nodes);
        }
    }

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
    
private static class NodePairIterator implements Iterator<NodePair> {
        
        Node[] nodes;
        int i = 0;
        int j = 1;
        NodePair pair = new NodePair();
        
        NodePairIterator(Node[] nodes) {
            this.nodes = nodes;
            if (nodes.length < 2) {
                i = nodes.length;
            }
        }

        public boolean hasNext() {
            return i < nodes.length;
        }

        public NodePair next() {
            if (i >= nodes.length) {
                throw new NoSuchElementException("Iterator exhausted");
            }
            pair.n1 = nodes[i];
            pair.n2 = nodes[j++];
            if (j >= nodes.length) {
                i++;
                j = i + 1;
                if (j >= nodes.length) {
                    i = nodes.length;
                }
            }
            return pair;
        }

        public void remove() {
            throw new UnsupportedOperationException("remove not supported");
        }
    }
    
    private static class NodePairIterable implements Iterable<NodePair> {
        Node[] nodes;
        NodePairIterable(Vector<Node> nodes) {
            this.nodes = nodes.toArray(new Node[nodes.size()]);
        }
        public Iterator<NodePair> iterator() {
            return new NodePairIterator(nodes);
        }
    }

    
    public synchronized Iterable<Node> getNodeIterable() {
        return new NodeIterable(nodes);
    }
    
    public synchronized Iterable<Edge> getEdgeIterable() {
        return new EdgeIterable(edges);
    }
    
    public synchronized Iterable<NodePair> getNodePairIterable() {
        return new NodePairIterable(nodes);
    }

    // Node manipulation ...........................

    /**
     * Return the number of Nodes in the cumulative Vector.
     * 
     * @deprecated this method has been replaced by the <tt>nodeCount()</tt>
     *             method.
     */
    public int nodeNum() {
        return nodes.size();
    }

    /** Return the number of Nodes in the cumulative Vector. */
    public int nodeCount() {
        return nodes.size();
    }

    /**
     * Return an iterator over the Nodes in the cumulative Vector, null if it is
     * empty.
     */
    /*
     * public Iterator getNodes() { if ( nodes.size() == 0 ) return null; return
     * nodes.iterator(); }
     */

    /**
     * Registers the Node <tt>node</tt> via its ID String <tt>id</tt>.
     * 
     * @param id
     *            the ID of the object.
     * @param node
     *            the Node to be registered.
     */
    // protected void registerNode( String id, Node node ) {
    // FIXME
    // }
    /**
     * Add the Node <tt>node</tt> to the graph, and registers the Node via its
     * ID. If no ID exists, no registration occurs.
     */
    public synchronized void addNode(Node node) throws TGException {
        String id = node.getID();
        if (id != null) {
            if (findNode(id) == null) { // doesn't already exist
                nodeIDRegistry.put(id, node);
                nodes.add(node);
            } else
                throw new TGException(TGException.NODE_EXISTS, "node ID '" + id
                        + "' already exists.");
        } else {
            String label = node.getLabel().trim();
            if (label == null)
                label = "";
            if (!label.equals("") && findNode(node.getLabel()) == null) {
                id = label;
            } else {
                int i;
                for (i = 1; findNode(label + "-" + i) != null; i++)
                    ;
                id = label + "-" + i;
            }
            node.setID(id);
            nodeIDRegistry.put(id, node);
            nodes.add(node);
        }
        // } else throw new TGException(TGException.NODE_NO_ID,"node has no
        // ID."); // could be ignored?
    }

    /** Returns true if the graph contains the Node <tt>node</tt>. */
    public boolean contains(Node node) {
        return nodes.contains(node);
    }

    // Edge manipulation ...........................

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

    /** Add the Edge <tt>edge</tt> to the graph. */
    public void addEdge(Edge edge) {
        if (edge == null)
            return;
        if (!contains(edge)) {
            edges.add(edge);
            edge.from.addEdge(edge);
            edge.to.addEdge(edge);
        }
    }

    /**
     * Add an Edge from Node <tt>from</tt> to Node <tt>to</tt>, with
     * tension of int <tt>tension</tt>, returning the Edge.
     */
    public Edge addEdge(Node from, Node to, int tension) {
        Edge edge = null;
        if (from != null && to != null) {
            edge = new Edge(from, to, tension);
            addEdge(edge);
        }
        return edge;
    }

    /** Returns true if the graph contains the Edge <tt>edge</tt>. */
    public boolean contains(Edge edge) {
        return edges.contains(edge);
    }

    /**
     * Return the Node whose ID matches the String <tt>id</tt>, null if no
     * match is found.
     */
    public Node findNode(String id) {
        if (id == null)
            return null; // ignore
        return nodeIDRegistry.get(id);
    }

    /**
     * Return the Node whose URL matches the String <tt>strURL</tt>, null if
     * no match is found.
     */
    public Node findNodeByURL(String strURL) {
        Node retVal = null;
        if (strURL == null)
            return null; // ignore

        Enumeration<Node> myEnum = nodeIDRegistry.elements();
        while (myEnum.hasMoreElements()) {
            Node node = myEnum.nextElement();
            if (node.getURL().equalsIgnoreCase(strURL)) {
                retVal = node;
                break;
            }
        }
        return retVal;
    }

    /**
     * Return a Collection of all Nodes whose label matches the String
     * <tt>label</tt>, null if no match is found.
     */
    /*
     * public Collection findNodesByLabel( String label ) { Vector nodelist =
     * new Vector(); for ( int i = 0 ; i < nodeCount() ; i++) { if
     * (nodeAt(i)!=null && nodeAt(i).getLabel().equals(label)) {
     * nodelist.add(nodeAt(i)); } } if ( nodelist.size() == 0 ) return null;
     * else return (Collection)nodelist; }
     */

    /**
     * Return the first Nodes whose label contains the String <tt>substring</tt>,
     * null if no match is found.
     */
    public Node findNodeLabelContaining(String substring) {
        for (Node n : nodes) {
            if (n.getLabel().toLowerCase().equals(
                            substring.toLowerCase())) {
                return n;
            }
        }
        
        for (Node n : nodes) {
            if (n.getLabel().toLowerCase().indexOf(
                            substring.toLowerCase()) > -1) {
                return n;
            }
        }
        return null;
    }

    /** Return an Edge spanning Node <tt>from</tt> to Node <tt>to</tt>. */
    public Edge findEdge(Node from, Node to) {
        for (Edge e : from.getEdgeIterable()) {
            if (e.to == to)
                return e;
        }
        return null;
    }

    /** Delete the Edge <tt>edge</tt>. */
    public boolean deleteEdge(Edge edge) {
        if (edge == null)
            return false;
        if (!edges.remove(edge))
            return false;
        edge.from.removeEdge(edge);
        edge.to.removeEdge(edge);
        return true;
    }

    /** Delete the Edges contained within the Vector <tt>edgedToDelete</tt>. */
    public void deleteEdges(Vector<Edge> edgesToDelete) {
        for (Edge e : edgesToDelete) {
            deleteEdge(e);
        }
    }

    /**
     * Delete the Edge spanning Node <tt>from</tt> to Node <tt>to</tt>,
     * returning true if successful.
     */
    public boolean deleteEdge(Node from, Node to) {
        Edge e = findEdge(from, to);
        if (e != null)
            return deleteEdge(e);
        return false;
    }

    /** Delete the Node <tt>node</tt>, returning true if successful. */
    public synchronized boolean deleteNode(Node node) {
        if (node == null)
            return false;
        if (!nodes.remove(node))
            return false;

        String id = node.getID();
        if (id != null)
            nodeIDRegistry.remove(id); // remove from registry

        for (Edge e : node.getEdgeIterable()) {
            if (e.from == node) {
                edges.remove(e); // Delete edge not used, because it
                                        // would change the node's edges
                e.to.removeEdge(e); // vector which is being iterated on.
            } else if (e.to == node) {
                edges.remove(e);
                e.from.removeEdge(e);
            }
            // No edges are deleted from node. Hopefully garbage collection
            // will pick them up.
        }
        return true;
    }

    /** Delete the Nodes contained within the Vector <tt>nodesToDelete</tt>. */
    public synchronized void deleteNodes(Vector<Node> nodesToDelete) {
        for (Node n : nodesToDelete) {
            deleteNode(n);
        }
    }

    /** Returns a random node, or null if none exist (for making random graphs). */
    public Node getRandomNode() {
        if (nodes.size() == 0)
            return null;
        int r = (int) (Math.random() * nodeCount());
        return nodes.get(r);
    }

    /** Return the first Node, null if none exist. */
    public Node getFirstNode() {
        if (nodes.size() == 0)
            return null;
        else
            return nodes.get(0);
    }

    /** Clear all nodes and edges. */
    public synchronized void clearAll() {
        nodes.clear();
        edges.clear();
        nodeIDRegistry.clear();
    }
 } // end com.touchgraph.graphlayout.graphelements.GraphEltSet
