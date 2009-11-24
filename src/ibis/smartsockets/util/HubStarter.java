package ibis.smartsockets.util;

import ibis.smartsockets.SmartSocketsProperties;
import ibis.smartsockets.direct.DirectSocketAddress;
import ibis.smartsockets.hub.Hub;

import java.io.IOException;

public class HubStarter {

    private static final int DEFAULT_ACCEPT_PORT = 17878;

    private static Hub h;

    public static void main(String[] args) {

        boolean startRouter = false;
        boolean startHub = true;

        DirectSocketAddress[] hubs = new DirectSocketAddress[args.length];
        int port = DEFAULT_ACCEPT_PORT;
        int numHubs = 0;

        // Load the default properties. These include the defaults in the code,
        // the default property file, and any command line '-D' settings.
        TypedProperties p = SmartSocketsProperties.getDefaultProperties();

        // SmartSocketsProperties can be adjusted further using old-fashioned
        // command
        // line options.
        for (int i = 0; i < args.length; i++) {

            if (args[i].startsWith("-external_router")) {
                startRouter = true;
            } else if (args[i].startsWith("-no-hub")) {
                startHub = false;
            } else if (args[i].equals("-clusters")) {
                if (i + 1 >= args.length) {
                    System.out.println("-clusters option requires parameter!");
                    System.exit(1);
                }

                String clusters = args[++i];

                p.put("smartsockets.hub.clusters", clusters);

                // Check if the property is a comma seperated list of strings
                String[] tmp = null;

                try {
                    tmp = p.getStringList("smartsockets.hub.clusters", ",",
                            null);
                } catch (Exception e) {
                    // ignore
                }

                if (tmp == null) {
                    System.out.println("-clusters option has incorrect "
                            + "parameter: " + clusters);
                    System.exit(1);
                }

            } else if (args[i].equals("-port")) {
                if (i + 1 >= args.length) {
                    System.out.println("-port option requires parameter!");
                    System.exit(1);
                }

                port = Integer.parseInt(args[++i]);

            } else {
                // Assume it's an address...
                try {
                    hubs[i] = DirectSocketAddress.getByAddress(args[i]);
                    numHubs++;
                } catch (Exception e) {
                    System.err.println("Skipping hub address: " + args[i]);
                    e.printStackTrace(System.err);
                }
            }
        }

        DirectSocketAddress[] tmp = new DirectSocketAddress[numHubs];

        int index = 0;

        for (int i = 0; i < hubs.length; i++) {
            if (hubs[i] != null) {
                tmp[index++] = hubs[i];
            }
        }

        hubs = tmp;

        if (port != DEFAULT_ACCEPT_PORT
                && (p.getIntProperty("smartsockets.hub.port", -1) != -1)) {
            p.put("smartsockets.hub.port", Integer.toString(port));
        }

        if (startHub) {
            try {
                h = new Hub(p);
                h.addHubs(hubs);

                System.out.println("Hub running on: " + h.getHubAddress());

            } catch (IOException e) {
                System.err.println("Oops: failed to start hub");
                e.printStackTrace(System.err);
                System.exit(1);
            }
        }

        if (startRouter) {

            DirectSocketAddress adr = null;

            if (h != null) {
                adr = h.getHubAddress();
            } else {
                adr = hubs[0];
            }

            if (adr == null) {
                System.err.println("Router requires hub address!");
                System.exit(1);
            }
        }

        //make sure this thread keeps living, otherwise the JVM will cease to
        //exist.
        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }
}
