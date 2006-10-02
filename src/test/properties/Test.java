package test.properties;

import java.io.FileInputStream;
import java.util.HashMap;
import ibis.connect.util.TypedProperties;

public class Test {
    
    
    private static void def(TypedProperties p, String prefix) { 
        
        String [] def = p.getStringList("define", ",", null);
        
        if (def == null || def.length == 0) { 
            System.out.println(prefix + ".define not found!");
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
        HashMap map = new HashMap();
        map.put("smartsockets.networks.default", "site,link,global");
        
        TypedProperties tp = new TypedProperties(map);
        
        if (args.length == 1) { 
            try { 
                FileInputStream in = new FileInputStream(args[0]);
                tp.load(in);
            } catch (Exception e) {
                System.err.println("Failed to load property file " + args[0]);
                System.exit(1);
            }
        }
        
        System.out.println("I now know " + tp.size() + " properties.");
        System.out.println();
        
        System.out.println("Network ======================== ");               
        TypedProperties tmp = tp.filter("smartsockets.networks.", true, true);
        def(tmp, "smartsockets.networks.");
                        
        
        System.out.println("Modules ======================== ");
        tmp = tp.filter("smartsockets.modules.", true, true); 
        def(tmp, "smartsockets.modules.");

        System.out.println("Cluster ======================== ");        
        tmp = tp.filter("smartsockets.cluster.", true, true);
        def(tmp, "smartsockets.cluster.");

        System.out.println("Leftover ======================== ");
                
        if (tp.size() > 0) {         
            System.out.println("I now the following 'other' properties:");
            System.out.println(tp.toVerboseString());
        }
        
        
        
    }    
}
