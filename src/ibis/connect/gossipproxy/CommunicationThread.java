package ibis.connect.gossipproxy;

import ibis.connect.virtual.VirtualSocketAddress;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Iterator;

abstract class CommunicationThread extends Thread {

    protected static final byte OPCODE_EOS      = 0;
    protected static final byte OPCODE_INFO     = 1;
    protected static final byte OPCODE_MESSAGES = 2;
    protected static final byte OPCODE_USER     = 3;
           
    protected GossipProxy owner;
    //protected final ActionList actions;
    protected final ProxyList knownProxies;
    protected final VirtualSocketAddress myMachineID;
    
    CommunicationThread(GossipProxy owner, VirtualSocketAddress myMachineID, 
           /* ActionList newMachines, */ ProxyList knownProxies) {
        
        super("CommunicationThread");
        
        this.owner = owner;
        this.myMachineID = myMachineID;
      //  this.actions = newMachines;
        this.knownProxies = knownProxies;
    } 
            
    protected void writeHeader(DataOutputStream dout, ProxyDescription d) 
        throws IOException {
        
        // First send our state and if we can reach the destination.
        dout.writeUTF(myMachineID.toString());
        dout.writeInt(knownProxies.size());
        dout.writeByte(d.reachable);        
    }
    
    protected ProxyDescription readHeader(DataInputStream din, 
            boolean myConnection) throws IOException { 
        
        // First receive the set of machines from the sender 
        VirtualSocketAddress sender = new VirtualSocketAddress(din.readUTF());
        int state = din.readInt();      
        byte canReachMe = din.readByte();
        
        // Check if we already know the sending machine
        ProxyDescription d = knownProxies.get(sender);
        
        if (d == null) {
            // The machine isn't know yet, so we've found a new machine!
            d = new ProxyDescription(sender, null, 0, state);
            knownProxies.add(d);                        
        } 

        // Check who initiated the connection and update the relevant info.         
        if (myConnection) {
            // I contacted him
            d.setReachable(knownProxies.size(), state, canReachMe);
        } else {         
            // He contacted me
            d.setCanReachMe(knownProxies.size(), state);
        }
        
        return d;             
    }
    
    
    protected void writeInformation(DataOutputStream dout, ProxyDescription dst)  
             throws IOException {

        boolean skippedDest = false;        
        long now = System.currentTimeMillis();
        
        // TODO: remove sync blok ?
        synchronized (knownProxies) {
            
            int total = knownProxies.size();
                        
            if (knownProxies.contains(dst.proxyAddress)) { 
                total -= 1;
            } else { 
                skippedDest = true;
            }
                        
            dout.writeInt(total);            
            Iterator itt = knownProxies.iterator();
                                  
            while (itt.hasNext()) { 
                ProxyDescription d = (ProxyDescription) itt.next();
            
                // Check if we're not sending info about dst to dst. 
                if (!skippedDest && dst.proxyAddress.equals(d.proxyAddress)) {
                    skippedDest = true;
                    // Nothing else to do here...
                } else {                                         
                    int lastContact = (int) (now - d.lastContact);
                
                    //System.err.println("Writing to " + dst + ": " + d.address 
                            //+ " " + d.lastKnownState + " " + d.lastContact 
                            //+ " " + lastContact);
                
                    dout.writeUTF(d.proxyAddress.toString());
                    dout.writeInt(d.lastKnownState);
                    dout.writeInt(lastContact);
                    dout.writeByte(d.reachable);
                    dout.writeByte(d.canReachMe);                    
                } 
            }
        }     
        
        /*
        int messages = dst.pendingMessages();
        
        dout.writeInt(messages);
        
        if (GossipSocketFactory.VERBOSE) {            
            if (messages > 0) { 
                System.out.println("Sending " + messages + " messages to " 
                        + dst.address + ":");
            }
        }
        
        for (int i=0;i<messages;i++) { 
            Message m = dst.getPendingMessage();
            
            if (GossipSocketFactory.VERBOSE) {                 
                System.out.println("  " + i + " - " + m);
            }
            
            Message.write(dout, m);
        }
        */
    }

    protected void readInformation(DataInputStream din, 
            ProxyDescription src) throws IOException {
        
        long now = System.currentTimeMillis(); 
        
        int size = din.readInt();

        for (int i=0;i<size;i++) {
            VirtualSocketAddress m = new VirtualSocketAddress(din.readUTF());
            int state = din.readInt();
            int lastContact = din.readInt();
            
            byte reachableFromMachine = din.readByte();
            byte canReachMachine = din.readByte();
                        
            //System.err.println("Reading from " + src + ": " + m + " " + state 
                    //+ " " + lastContact); 
                        
            // Check if it the machine is already known.             
            ProxyDescription tmp = knownProxies.get(m);
            
            if (tmp == null) {
                // It's a new machine, so we add it
                tmp = new ProxyDescription(m, src.proxyAddress, 0, state);
                knownProxies.add(tmp);                    
              //  actions.exchangeInfo(tmp);
            } else {
                // We already know the machine. Because the machine that we
                // are currently talking to (src) should at least contain 
                // the information that 'tmp' contained when it was in 
                // 'state', we can safely update the state here...                    
                if (tmp.lastKnownState < state) { 
                    tmp.lastKnownState = state;
                }
                
                // Check if the machine was previously labelled as 
                // 'unreachable'. If so, we can now reach it through 'src',  
                // provided that we can reach 'src' of course...                     
                if (tmp.reachable == ProxyDescription.UNREACHABLE &&
                        reachableFromMachine == ProxyDescription.DIRECT &&                         
                        src.reachable == ProxyDescription.DIRECT) {                     
                    tmp.reachable = ProxyDescription.INDIRECT;
                    tmp.indirection = src.proxyAddress;              
                }
                
                // Update the 'lastContact' timestamp to the most recent 
                // value we can find. First convert the relative time 
                // (i.e., X milliseconds ago) to a local timestamp.                                       
                tmp.setContactTimeStamp(now - lastContact);
            }
        }
        
        /*
        int messages = din.readInt();
        
        if (GossipSocketFactory.VERBOSE) { 
            if (messages > 0) { 
                System.out.println("Receiving " + messages + " messages from " 
                        + src.address + ":");
            }
        }
        
        for (int i=0;i<messages;i++) {
            Message m = Message.read(din);
            
            if (GossipSocketFactory.VERBOSE) {                    
                System.out.println("  " + i + " - " + m);
            }
                        
            actions.processMessage(m);
        }
        */
    }    
}
