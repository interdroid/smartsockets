package ibis.connect.virtual;

import ibis.util.TypedProperties;

public class Properties {

    public static final String PREFIX = "ibis.connect.virtual.";
    
    // These are used by the ParallelStreamSocketFactory 
    //public static final String PAR_NUMWAYS = PREFIX + "NumWays";
    //public static final String PAR_BLOCKSIZE = PREFIX + "BlockSize";

    // These are used by the RoutedMessagesSocketFactory
    //public static final String HUB_PORT = PREFIX + "hub.port";
    public static final String HUB_HOST = PREFIX + "hub.host";
    public static final String HUB_TAG = PREFIX + "hub.tag";
    //public static final String HUB_STATS = PREFIX + "hub.stats";
    
    public static final String ROUTERS = PREFIX + "routers";  
    
    // These are used by the TcpSpliceSocketFactory    
    /** Splice timeout: 0 means try splicing forever, < 0 means do not try slicing,
    > 0 means try splicing for slice_timeout seconds */
    //public static final String SPLICE_TIMEOUT = PREFIX + "splice_timeout";
    //public static final String SPLICE_PORT = PREFIX + "splice_port";

    // These are generic ones
    private static final String DEBUG_PROP = PREFIX + "debug";    
    private static final String VERBOSE_PROP = PREFIX + "verbose";
    
    public static final String ISIZE = PREFIX + "InputBufferSize";
    public static final String OSIZE = PREFIX + "OutputBufferSize";

    public static final boolean DEBUG = TypedProperties.booleanProperty(
            DEBUG_PROP, false);

    public static final boolean VERBOSE = TypedProperties.booleanProperty(
            VERBOSE_PROP, false);

    public static int inputBufferSize = 64 * 1024;
    public static int outputBufferSize = 64 * 1024;
      
    private static final String[] sysprops = { 
            /*PAR_NUMWAYS, PAR_BLOCKSIZE, HUB_PORT,*/ HUB_HOST, HUB_TAG, 
            /*HUB_STATS, SPLICE_PORT, SPLICE_TIMEOUT,*/ DEBUG_PROP, VERBOSE_PROP,
            ISIZE, OSIZE };

    static {
        TypedProperties.checkProperties(PREFIX, sysprops, null);
        inputBufferSize = TypedProperties.intProperty(ISIZE, 64 * 1024);
        outputBufferSize = TypedProperties.intProperty(OSIZE, 64 * 1024);
    }
}
