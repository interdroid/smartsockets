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
    String message; 
    int hopsLeft;
    
    ClientMessage(SocketAddressSet source, SocketAddressSet sourceHub, 
            int hopsLeft, DataInputStream in) throws IOException {
        
        this.source = source;
        this.sourceHub = sourceHub;
        
        target = new SocketAddressSet(in.readUTF());
        
        String tmp = in.readUTF();
        
        if (tmp.length() > 0) {         
            targetHub = new SocketAddressSet(tmp);               
        }
        
        module = in.readUTF();
        code = in.readInt();
        message = in.readUTF();        
    }
    
    ClientMessage(DataInputStream in) throws IOException {                
        source = new SocketAddressSet(in.readUTF());
        sourceHub = new SocketAddressSet(in.readUTF());        
        
        target = new SocketAddressSet(in.readUTF());
        
        String tmp = in.readUTF();
        
        if (tmp.length() > 0) {         
            targetHub = new SocketAddressSet(tmp);               
        }
        
        module = in.readUTF();
        code = in.readInt();
        message = in.readUTF();
        
        hopsLeft = in.readInt();
    }
    
    void write(DataOutputStream out) throws IOException { 
    
        out.writeUTF(source.toString());
        out.writeUTF(sourceHub.toString());
    
        out.writeUTF(target.toString());
        
        if (targetHub == null) { 
            out.writeUTF("");
        } else { 
            out.writeUTF(targetHub.toString());
        } 
        
        out.writeUTF(module);
        out.writeInt(code);
        out.writeUTF(message);
        out.writeInt(hopsLeft);       
    }
    
    void writePartially(DataOutputStream out) throws IOException { 
        
        out.writeUTF(source.toString());
        out.writeUTF(sourceHub.toString());
           
        out.writeUTF(module);
        out.writeInt(code);
        out.writeUTF(message);
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
            + code + "] message: " + message;        
    }
    
}
