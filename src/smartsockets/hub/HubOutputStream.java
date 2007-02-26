package smartsockets.hub;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import smartsockets.direct.DirectSocketAddress;

public class HubOutputStream extends DataOutputStream {

    public HubOutputStream(OutputStream out) {
        super(out);
    }

    public void writeSocketAddressSet(DirectSocketAddress a) throws IOException { 
        
        byte [] codedForm = a.getAddress();
        
        writeInt(codedForm.length);
        write(codedForm);
    }
    
}
