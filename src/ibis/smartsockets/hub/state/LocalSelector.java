package ibis.smartsockets.hub.state;

public class LocalSelector extends Selector {

    private HubDescription local;

    public boolean needLocal() {
        return true;
    }

    public void select(HubDescription description) {
        local = description;
    }

    public HubDescription getResult() {
        return local;
    }
}
