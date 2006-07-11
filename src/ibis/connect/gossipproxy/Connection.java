/**
 * 
 */
package ibis.connect.gossipproxy;

import ibis.connect.direct.DirectSocket;
import ibis.connect.util.Forwarder;

import java.io.InputStream;
import java.io.OutputStream;

class Connection { 
    
    int number; 
    
    String id; 
    
    String clientAsString;
    DirectSocket socketA;
    InputStream inA;
    OutputStream outA;        
    
    String targetAsString;
    DirectSocket socketB;        
    InputStream inB;
    OutputStream outB;
    
    Forwarder forwarder1; // forwards from inA to outB         
    Forwarder forwarder2; // forwards from inB to outA
    
    Connection(String clientAsString, String targetAsString, int number, 
            DirectSocket s, InputStream in, OutputStream out) { 
       
        id = "[" + number + ": " + clientAsString + " <--> " + targetAsString + "]";

        this.number = number;
        
        this.clientAsString = clientAsString;
        this.targetAsString = targetAsString;
            
        this.socketA = s;
        this.inA = in;
        this.outA = out;    
    }
            
    public String toString() { 
        return id;         
    }    
}