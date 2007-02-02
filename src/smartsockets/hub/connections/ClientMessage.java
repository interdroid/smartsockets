package smartsockets.hub.connections;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import smartsockets.direct.SocketAddressSet;

public class ClientMessage {

    SocketAddressSet source;
    SocketAddressSet sourceHub;                      
    SocketAddressSet target; 
    SocketAddressSet targetHub;               
    String module; 
    int code; 
    byte [] message;    
    int hopsLeft;
    
    ClientMessage(SocketAddressSet source, SocketAddressSet sourceHub, 
            int hopsLeft, DataInputStream in) throws IOException {
        
        this.source = source;
        this.sourceHub = sourceHub;
        
        target = SocketAddressSet.read(in);
        targetHub = SocketAddressSet.read(in);
                
        module = in.readUTF();
        code = in.readInt();
        
        int len = in.readInt();
        
        if (len > 0) { 
            message = new byte[len];        
            in.readFully(message);
        }
    }
    
    ClientMessage(DataInputStream in) throws IOException {
        this(SocketAddressSet.read(in), SocketAddressSet.read(in), in.readInt(),
                in);
    }
    
    void write(DataOutputStream out) throws IOException { 
    
        SocketAddressSet.write(source, out);
        SocketAddressSet.write(sourceHub, out);

        out.writeInt(hopsLeft);       
        
        SocketAddressSet.write(target, out);
        SocketAddressSet.write(targetHub, out);
        
        out.writeUTF(module);
        out.writeInt(code);
        
        if (message == null || message.length == 0) { 
            out.writeInt(0);
        } else { 
            out.writeInt(message.length);
            out.write(message);
        }
    }
    
    void writePartially(DataOutputStream out) throws IOException { 

        SocketAddressSet.write(source, out);
        SocketAddressSet.write(sourceHub, out);
           
        out.writeUTF(module);
        out.writeInt(code);
        
        if (message == null || message.length == 0) { 
            out.writeInt(0);
        } else { 
            out.writeInt(message.length);
            out.write(message);
        }
    }       

    String target() {        
        if (targetHub != null) { 
            return target + "@" + targetHub;
        } else { 
            return target.toString();
        }
    }
    
    String source() { 
        return source + "@" + sourceHub;
    }
    
    public String toString() {         
        return "Message [from " + source + "@" + sourceHub + "] [to " 
            + target + "@" + targetHub + "] [module " + module + " code " 
            + code + "] message: [" + (message == null ? 0 : message.length) 
            + "]";        
    }    
}
