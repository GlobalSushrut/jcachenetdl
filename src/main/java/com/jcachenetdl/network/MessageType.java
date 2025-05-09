package com.jcachenetdl.network;

/**
 * Enumeration of message types that can be exchanged in the network.
 */
public enum MessageType {
    JOIN("JOIN"),                       // Node announces itself to peers
    JOIN_RESPONSE("JOIN_RESPONSE"),     // Response to a JOIN message
    FILE_REQUEST("FILE_REQUEST"),       // Peer asks for a file chunk
    FILE_RESPONSE("FILE_RESPONSE"),     // Serves the requested chunk
    LEDGER_SYNC("LEDGER_SYNC"),         // Peer requests to sync ledger blocks
    LEDGER_SYNC_RESPONSE("LEDGER_SYNC_RESPONSE"), // Response to a LEDGER_SYNC message
    LEDGER_ENTRY("LEDGER_ENTRY"),       // Propagate a new ledger entry
    PEER_LIST("PEER_LIST"),             // Share list of known peers
    PING("PING"),                       // Check if a peer is alive
    PONG("PONG");                       // Response to a PING message
    
    private final String value;
    
    MessageType(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    public static MessageType fromValue(String value) {
        for (MessageType type : MessageType.values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown message type: " + value);
    }
}
