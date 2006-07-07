/**
 * 
 */
package ibis.connect.gossipproxy;

import ibis.connect.virtual.VirtualSocket;

import java.io.InputStream;
import java.io.OutputStream;

class Connection { 
    
    String id; 
    
    String clientAsString;
    VirtualSocket socketA;
    InputStream inA;
    OutputStream outA;        
    
    String targetAsString;
    VirtualSocket socketB;        
    InputStream inB;
    OutputStream outB;
    
    Forwarder forwarder1; // forwards from inA to outB         
    Forwarder forwarder2; // forwards from inB to outA
    
    Connection(String clientAsString, String targetAsString, int number, 
            VirtualSocket s, InputStream in, OutputStream out) { 

        id = "[" + number + ":" + clientAsString + "->" + targetAsString + "]";
            
        this.clientAsString = clientAsString;
        this.targetAsString = targetAsString;
            
        this.socketA = s;
        this.inA = in;
        this.outA = out;    
    }
            
            
    
}