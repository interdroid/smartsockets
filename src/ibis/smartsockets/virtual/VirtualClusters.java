package ibis.smartsockets.virtual;

import ibis.smartsockets.SmartSocketsProperties;
import ibis.smartsockets.direct.DirectSocketAddress;
import ibis.smartsockets.util.TypedProperties;
import ibis.smartsockets.virtual.modules.ConnectModule;

import java.util.Arrays;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class VirtualClusters {

    protected static final Logger logger = LoggerFactory.getLogger("ibis.smartsockets.virtual.clustering");


    private static class ClusterDefinition {
        final String name;
        final ConnectModule [] order;

        ClusterDefinition(String name, ConnectModule [] order) {
            this.name = name;
            this.order = order;

            logger.info("Created cluster definition " + name + " "
                    + Arrays.deepToString(order));
        }

        public void reorder(ConnectModule m) {

            if (order[0] == m) {
                return;
            }

            int index = -1;

            for (int i=1;i<order.length;i++) {
                if (order[i] == m) {
                    index = i;
                    break;
                }
            }

            if (index == -1) {
                logger.warn("Oops: Module " + m.getName() + " not found while"
                        + " reordering cluster " + name);
                return;
            }

            ConnectModule tmp = order[0];
            order[0] = m;
            order[index] = tmp;
        }
    }

    private final boolean reorder;
    private final String localCluster;
    private final ClusterDefinition defaultOrder;
    private final HashMap<String, ClusterDefinition> clusters =
        new HashMap<String, ClusterDefinition>();

    private final HashMap<VirtualSocketAddress, ClusterDefinition> orphans =
        new HashMap<VirtualSocketAddress, ClusterDefinition>();

    VirtualClusters(VirtualSocketFactory parent,
            TypedProperties properties, ConnectModule [] order) {

        reorder = properties.booleanProperty(SmartSocketsProperties.CLUSTER_REORDER, true);
        String myc = properties.getProperty(SmartSocketsProperties.CLUSTER_MEMBER, null);

        if (myc == null || myc.length() == 0) {
            // We don't belong to any cluster, so it's no use parsing the rest
            // of the cluster definitions...
            localCluster = "";
            this.defaultOrder = new ClusterDefinition("default", order);
            logger.info("I am not a member of any of the virtual clusters!");
            return;
        }

        localCluster = myc;

        String [] clusters = properties.getStringList(
                SmartSocketsProperties.CLUSTER_DEFINE, ",",
                new String [] { localCluster });

        logger.info("Clusters defined: " + Arrays.deepToString(clusters));

        int myCluster = -1;

        for (int i=0;i<clusters.length;i++) {
            if (clusters[i].equals(localCluster)) {
                myCluster = i;
                break;
            }
        }

        if (myCluster == -1) {
            logger.info("Missing the virtual cluster definition for my cluster:"
                    + localCluster);
            this.defaultOrder = new ClusterDefinition("default", order);
            return;
        }

        logger.info("Processing cluster definitions:");
        logger.info("  - my cluster: " + localCluster);

        String prefix = SmartSocketsProperties.CLUSTER_PREFIX + localCluster + ".";

        // First get the 'default' connect rule
        String p = prefix + SmartSocketsProperties.CLUSTER_DEFAULT;
        String [] tmp = properties.getStringList(p, ",", null);

        if (tmp != null && tmp.length > 0) {
            this.defaultOrder =
                new ClusterDefinition("default", parent.getModules(tmp));
        } else {
            this.defaultOrder = new ClusterDefinition("default", order);
        }

        logger.info("  - default : " + Arrays.deepToString(tmp));

        // Next, get the rule for how we are supposed to connect inside our own
        // cluster...
        p = prefix + SmartSocketsProperties.CLUSTER_INSIDE;
        tmp = properties.getStringList(p, ",", null);

        if (tmp != null && tmp.length > 0) {
            addCluster(localCluster, parent.getModules(tmp));
        }

        // Finally, extract the connect rules for any of the other clusters...
        for (int i=0;i<clusters.length;i++) {
            if (i != myCluster) {
                p = prefix + SmartSocketsProperties.CLUSTER_PREFERENCE + clusters[i];

                tmp = properties.getStringList(p, ",", null);

                if (tmp != null && tmp.length > 0) {
                    addCluster(clusters[i], parent.getModules(tmp));
                }
            }
        }
    }

    private void addCluster(String name, ConnectModule [] order) {
        logger.info("  - to " + name + " : " + Arrays.deepToString(order));
        clusters.put(name, new ClusterDefinition(name, order));
    }

    public final String localCluster() {
        return localCluster;
    }

    private ConnectModule[] getSingleNodeOrder(VirtualSocketAddress target) {

        if (!reorder) {
            return defaultOrder.order;
        }

        if (logger.isInfoEnabled()) {
            logger.info("Determine connect order for node: " + target.toString());
        }

        ClusterDefinition d = orphans.get(target);

        if (d == null) {
            d = new ClusterDefinition("orphan", defaultOrder.order.clone());
            orphans.put(target, d);
        }

        if (logger.isInfoEnabled()) {
            logger.info("Connect order: " + Arrays.deepToString(d.order));
        }

        return d.order;
    }

    public ConnectModule[] getOrder(VirtualSocketAddress target) {

        // Get the cluster of the target machine
        String c = target.cluster();

        if (c == null || c.length() == 0) {
            // No cluster defined... let's use the hub address as a cluster.
            DirectSocketAddress hub = target.hub();

            if (hub != null) {
                c = hub.toString();
            }
        }

        if (c == null || c.length() == 0) {
            // Handle 'orphan' nodes (without cluster or hub) seperately...
            ConnectModule[] result = getSingleNodeOrder(target);

            if (result == null) {
                return defaultOrder.order;
            }
        }

        if (logger.isInfoEnabled()) {
            logger.info("Determine connect order for cluster: " + c);
        }

        // Get the cluster definition of the target cluster...
        ClusterDefinition d = clusters.get(c);

        if (d == null) {
            // No definition is found!
            if (!reorder) {
                // We are not allowed to create new clusters, so we just return
                // the default order
                return defaultOrder.order;
            }

            if (logger.isInfoEnabled()) {
                logger.info("New cluster found: " + c);
            }

            // Let's get creative!
            // NOTE: Don't forget to -clone- the default order!
            d = new ClusterDefinition(c, defaultOrder.order.clone());
            clusters.put(c, d);
        }

        if (logger.isInfoEnabled()) {
            logger.info("Order found for cluster: " + c + " -> "
                    + Arrays.deepToString(d.order));
        }

        return d.order;
    }


    public void succes(VirtualSocketAddress target, ConnectModule m) {

        if (!reorder) {
            return;
        }

        String c = target.cluster();

        ClusterDefinition d = null;

        if (c == null || c.length() == 0) {
            // If no order is defined we use the hub.
            DirectSocketAddress hub = target.hub();

            if (hub != null) {
                c = hub.toString();
            }
        }

        if (c == null || c.length() == 0) {
            // Handle 'orphan' nodes seperately...
            if (logger.isInfoEnabled()) {
                logger.info("Caching connect order for node: "
                        + target.toString());
            }
            d = orphans.get(target);
        } else {
            //  Get the cluster definition of the target cluster...
            if (logger.isInfoEnabled()) {
                logger.info("Caching connect order for cluster: " + c);
            }
            d = clusters.get(c);
        }

        if (d != null) {
            d.reorder(m);
        }
    }
}

