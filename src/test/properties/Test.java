package test.properties;

import ibis.smartsockets.util.TypedProperties;

import java.io.FileInputStream;
import java.util.HashMap;


public class Test {


    private static void def(TypedProperties p, String prefix) {

        String [] def = p.getStringList("define", ",", null);

        if (def == null || def.length == 0) {
            System.out.println(prefix + "define not found!");
            return;
        }

        System.out.println("Found " + def.length + " definitions");

        for (int i=0;i<def.length;i++) {
            System.out.println(i + " " + def[i]);

            TypedProperties tmp = p.filter(def[i] + ".", true, true);
            System.out.println(tmp.toVerboseString());
        }

        System.out.println("Leftover");
        System.out.println(p.toVerboseString());
    }

    public static void main(String [] args) {

        // These are the default values.....
        HashMap<String, String> defaults = new HashMap<String, String>();
        defaults.put("ibis.smartsockets.networks.default", "site,link,global");

        TypedProperties tp = new TypedProperties(defaults);

        if (args.length == 1) {
            try {
                FileInputStream in = new FileInputStream(args[0]);
                tp.load(in);
            } catch (Exception e) {
                System.err.println("Failed to load property file " + args[0]);
                System.exit(1);
            }
        }

        TypedProperties sys = new TypedProperties(System.getProperties());

        tp.putAll(sys.filter("ibis.smartsockets.", false, false));

        System.out.println("I now know " + tp.size() + " properties.");
        System.out.println(tp.toVerboseString());

        System.out.println("Network ======================== ");
        TypedProperties tmp = tp.filter("ibis.smartsockets.networks.", true, true);
        def(tmp, "ibis.smartsockets.networks.");


        System.out.println("Modules ======================== ");
        tmp = tp.filter("ibis.smartsockets.modules.", true, true);
        def(tmp, "ibis.smartsockets.modules.");

        System.out.println("Cluster ======================== ");
        tmp = tp.filter("ibis.smartsockets.cluster.", true, true);
        def(tmp, "ibis.smartsockets.cluster.");

        System.out.println("Leftover ======================== ");

        if (tp.size() > 0) {
            System.out.println("I now the following 'other' properties:");
            System.out.println(tp.toVerboseString());
        }



    }
}
