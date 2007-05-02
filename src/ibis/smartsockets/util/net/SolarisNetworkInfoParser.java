package ibis.smartsockets.util.net;

import java.util.List;

public class SolarisNetworkInfoParser extends NetworkInfoParser {

    public SolarisNetworkInfoParser() { 
        super("Solaris");
    }
    
    String[] getCommand(int number) {
        // TODO Auto-generated method stub
        return null;
    }

    int numberOfCommands() {
        // TODO Auto-generated method stub
        return 0;
    }

    boolean parse(byte[] output, List info) {
        // TODO Auto-generated method stub
        return false;
    }
}