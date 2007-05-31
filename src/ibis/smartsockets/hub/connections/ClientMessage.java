package ibis.smartsockets.hub.connections;

import ibis.smartsockets.direct.DirectSocketAddress;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class ClientMessage {

    private DirectSocketAddress source;
    private DirectSocketAddress sourceHub;                      
    private DirectSocketAddress target; 
    private DirectSocketAddress targetHub;            
    
    int hopsLeft;
    boolean returnToSender;
    
    String module; 
    int code; 
    byte [] message;    
    
    ClientMessage(DataInputStream in) throws IOException {
        
        source = DirectSocketAddress.read(in);
        sourceHub = DirectSocketAddress.read(in);       
        
        hopsLeft = in.readInt();
        returnToSender = in.readBoolean(); 
        
        target = DirectSocketAddress.read(in);
        targetHub = DirectSocketAddress.read(in);
                
        module = in.readUTF();
        code = in.readInt();
        
        int len = in.readInt();
        
        if (len > 0) { 
            message = new byte[len];        
            in.readFully(message);
        }
    }
    
    void write(DataOutputStream out) throws IOException { 
    
        DirectSocketAddress.write(source, out);
        DirectSocketAddress.write(sourceHub, out);

        out.writeInt(hopsLeft);       
        out.writeBoolean(returnToSender);
        
        DirectSocketAddress.write(target, out);
        DirectSocketAddress.write(targetHub, out);
        
        out.writeUTF(module);
        out.writeInt(code);
        
        if (message == null || message.length == 0) { 
            out.writeInt(0);
        } else { 
            out.writeInt(message.length);
            out.write(message);
        }
    }
    
    DirectSocketAddress getSource() { 
        if (returnToSender) { 
            return target;
        } else { 
            return source;
        }
    }
    
    DirectSocketAddress getSourceHub() { 
        if (returnToSender) { 
            return targetHub;
        } else { 
            return sourceHub;
        }
    }
    
    DirectSocketAddress getTarget() { 
        if (returnToSender) { 
            return source;
        } else { 
            return target;
        }
    }
    
    DirectSocketAddress getTargetHub() { 
        if (returnToSender) { 
            return sourceHub;
        } else { 
            return targetHub;
        }
    }
    
    
    String targetAsString() {        
        if (targetHub != null) { 
            return target + "@" + targetHub;
        } else { 
            return target.toString();
        }
    }
    
    String sourceAsString() { 
        return source + "@" + sourceHub;
    }
    
    public String toString() {         
        return "Message [from " + source + "@" + sourceHub + "] [to " 
            + target + "@" + targetHub + "] [module " + module + " code " 
            + code + "] message: [" + (message == null ? 0 : message.length) 
            + "]";        
    }

    public void setSourceHub(DirectSocketAddress hub) {
        if (sourceHub == null) { 
            sourceHub = hub;
        }
    }    
}
