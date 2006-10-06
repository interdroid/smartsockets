package smartsockets.hub.connections;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class ClientMessage {

    String source;
    String sourceHub;                      
    String target; 
    String targetHub;               
    String module; 
    int code; 
    String message; 
    int hopsLeft;
    
    ClientMessage(String source, String sourceHub, int hopsLeft, 
            DataInputStream in) throws IOException {
        
        this.source = source;
        this.sourceHub = sourceHub;
        
        target = in.readUTF();
        targetHub = in.readUTF();               
        module = in.readUTF();
        code = in.readInt();
        message = in.readUTF();        
    }
    
    ClientMessage(DataInputStream in) throws IOException { 
        source = in.readUTF();
        sourceHub = in.readUTF();        
        
        target = in.readUTF();
        targetHub = in.readUTF();               
        
        module = in.readUTF();
        code = in.readInt();
        message = in.readUTF();
        
        hopsLeft = in.readInt();
    }
    
    void write(DataOutputStream out) throws IOException { 
    
        out.writeUTF(source);
        out.writeUTF(sourceHub);
    
        out.writeUTF(target);
        out.writeUTF(targetHub);
        
        out.writeUTF(module);
        out.writeInt(code);
        out.writeUTF(message);
        out.writeInt(hopsLeft);       
    }
    
    void writePartially(DataOutputStream out) throws IOException { 
        
        out.writeUTF(source);
        out.writeUTF(sourceHub);
           
        out.writeUTF(module);
        out.writeInt(code);
        out.writeUTF(message);
    }       

    String target() { 
        return target + "@" + targetHub;
    }
    
    String source() { 
        return source + "@" + sourceHub;
    }
    
    
    
}
