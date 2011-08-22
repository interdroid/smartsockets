package ibis.smartsockets.hub.state;

import ibis.smartsockets.direct.DirectSocketAddress;

import java.util.LinkedList;


public class AddressSelector extends Selector {

    private LinkedList<DirectSocketAddress> result = new LinkedList<DirectSocketAddress>();

    public boolean needAll() {
        return true;
    }

    public void select(HubDescription description) {

        if (description.getConnection() != null) {
            result.add(description.hubAddress);
        }
    }

    public LinkedList<DirectSocketAddress> getResult() {
        return result;
    }
}
