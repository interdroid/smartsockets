package test.direct.simple;

import ibis.smartsockets.util.NetworkUtils;

public class UUIDTest {

    public static void main(String [] args) { 
        byte [] uuid = NetworkUtils.getUUID();
        System.out.println("My UUID: " + NetworkUtils.UUIDToString(uuid));
    }
}
