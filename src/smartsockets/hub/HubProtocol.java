package smartsockets.hub;

import smartsockets.hub.connections.MessageForwarderProtocol;

public interface HubProtocol extends MessageForwarderProtocol {
    
    public static final byte GOSSIP = 20;
} 
