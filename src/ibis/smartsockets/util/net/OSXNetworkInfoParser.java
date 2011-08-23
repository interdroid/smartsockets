package ibis.smartsockets.util.net;

import java.util.List;

public class OSXNetworkInfoParser extends NetworkInfoParser {

    public OSXNetworkInfoParser() {
        super("Mac OS X");
    }

    String[] getCommand(int number) {
        // TODO Auto-generated method stub
        return null;
    }

    int numberOfCommands() {
        // TODO Auto-generated method stub
        return 0;
    }

    boolean parse(byte[] output, List<NetworkInfo> info) {
        // TODO Auto-generated method stub
        return false;
    }

}
