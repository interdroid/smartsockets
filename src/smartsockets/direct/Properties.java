package smartsockets.direct;

import ibis.util.TypedProperties;

/**
 * Collects all system properties used by the ibis.connect package and
 * sub-packages.
 */
class Properties {

    public static final String PREFIX = "ibis.connect.direct.";
    
    // These are used by the PlainSocketFactory
    public static final String USE_NIO = PREFIX + "nio";
    public static final String USE_STUN = PREFIX + "stun";
    public static final String USE_UPNP = PREFIX + "upnp";
    public static final String USE_BOUNCER = PREFIX + "bouncer";
    public static final String PORT_RANGE = PREFIX + "port_range";
    public static final String EXTERNAL_ADDR = PREFIX + "external_address";
    public static final String BOUNCERS = PREFIX + "bouncer_address";    
    public static final String CONNECT_FILE = PREFIX + "preference_file";   
    
    // These are generic ones
    private static final String DEBUG_PROP = PREFIX + "debug";    
    private static final String VERBOSE_PROP = PREFIX + "verbose";
    
    public static final String IN_BUF_SIZE = PREFIX + "InputBufferSize";
    public static final String OUT_BUF_SIZE = PREFIX + "OutputBufferSize";

    public static final boolean DEBUG = TypedProperties.booleanProperty(
            DEBUG_PROP, false);

    public static final boolean VERBOSE = TypedProperties.booleanProperty(
            VERBOSE_PROP, false);

    public static int inputBufferSize = 64 * 1024;
    public static int outputBufferSize = 64 * 1024;
          
    private static final String[] sysprops = { 
        USE_NIO, USE_STUN, USE_UPNP, USE_BOUNCER, PORT_RANGE, EXTERNAL_ADDR, 
        BOUNCERS, DEBUG_PROP, VERBOSE_PROP, IN_BUF_SIZE, OUT_BUF_SIZE };

    static {
        TypedProperties.checkProperties(PREFIX, sysprops, null);
        inputBufferSize = TypedProperties.intProperty(IN_BUF_SIZE, 64 * 1024);
        outputBufferSize = TypedProperties.intProperty(OUT_BUF_SIZE, 64 * 1024);
    }
}
