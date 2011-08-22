package ibis.smartsockets.hub;

import ibis.smartsockets.direct.DirectSocketAddress;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;


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
