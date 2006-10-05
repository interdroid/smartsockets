package smartsockets.direct;

import ibis.util.TypedProperties;
import java.util.StringTokenizer;

class PortRange {

    static final class Range {         
        final int start;
        final int end;      
        
        Range next;
        
        Range(int start, int end) { 
            this.start = start;
            this.end = end;
        }
    }

    private Range ranges;    
    private int port = -1;
        
    /**
     * Construct a new PortRange by reading the range of port numbers that may 
     * be used from the 'ibis.connect.port_range' property. The property is 
     * expected to have the following format:
     * 
     *  RANGE(,RANGE)* 
     *  
     * where RANGE has the format
     * 
     *   P         to specify a single port number
     *   P1-P2     to specify a port range from P1 to P2 (inclusive) 
     * 
     * When the property is not set, the getPort method will always return 0; 
     */
    PortRange() { 

        String range = TypedProperties.stringProperty(
                Properties.PORT_RANGE);
        
        if (range == null || range.length() == 0) { 
            return;
        }
        
        StringTokenizer tok = new StringTokenizer(range, ",");

        while (tok.hasMoreTokens()) { 
            String t = tok.nextToken();
            
            int index = t.indexOf('-');
                               
            int start;
            int end;
            
            if (index == -1) { 
                // it's a single port
                start = end = Integer.parseInt(t);
            } else { 
                // it's a range
                start = Integer.parseInt(t.substring(0, index));
                end = Integer.parseInt(t.substring(index+1));
            }
            
            add(start, end);
        }
    }
    
    /**
     * Add a new port range to this datastructure
     * 
     * @param start of the range (inclusive) 
     * @param end of the range (inclusive)
     */
    private void add(int start, int end) {
        Range range = new Range(start, end);
        
        if (ranges == null) { 
            ranges = range;
            range.next = range;
        } else {             
            range.next = ranges.next;
            ranges.next = range;
        }
    }

    /**
     * Returns a port number within the give port range, or 0 if no range has 
     * been defined.
     * 
     * @return port value.
     */
    int getPort() {
        
        // Return 0 if no port range is defined
        if (ranges == null) { 
            return 0;
        } 
        
        if (port == -1) { 
            // First time a port is requested
            port = ranges.start;            
        } else {
            port++;
            
            // Check if we ran out of the range
            if (port > ranges.end) {
                // we did, so go on to the next one
                ranges = ranges.next;
                port = ranges.start;
            }
        } 
        
        return port;    
    }
}
