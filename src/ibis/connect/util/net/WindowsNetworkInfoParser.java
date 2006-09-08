package ibis.connect.util.net;

import java.util.List;

public class WindowsNetworkInfoParser extends NetworkInfoParser {

    public WindowsNetworkInfoParser() { 
        super("Windows");
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
