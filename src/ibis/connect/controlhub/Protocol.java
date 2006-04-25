package ibis.connect.controlhub;

public interface Protocol {

    public final byte CONNECT          = 42;
    public final byte CONNECT_ACCEPTED = 43;
    public final byte CONNECT_REFUSED  = 44;
    public final byte DISCONNECT       = 45;      
    public final byte MESSAGE          = 16;
    
}
