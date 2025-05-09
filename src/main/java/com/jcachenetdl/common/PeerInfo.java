package com.jcachenetdl.common;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents information about a peer node in the network.
 */
public class PeerInfo implements Serializable {
    private String id;
    private String host;
    private int port;
    private long lastSeen;
    private boolean active;
    
    public PeerInfo(String id, String host, int port) {
        this.id = id;
        this.host = host;
        this.port = port;
        this.lastSeen = System.currentTimeMillis();
        this.active = true;
    }
    
    // Getters and setters
    public String getId() { 
        return id; 
    }
    
    public void setId(String id) { 
        this.id = id; 
    }
    
    public String getHost() { 
        return host; 
    }
    
    public void setHost(String host) { 
        this.host = host; 
    }
    
    public int getPort() { 
        return port; 
    }
    
    public void setPort(int port) { 
        this.port = port; 
    }
    
    public long getLastSeen() { 
        return lastSeen; 
    }
    
    public void setLastSeen(long lastSeen) { 
        this.lastSeen = lastSeen; 
    }
    
    public boolean isActive() { 
        return active; 
    }
    
    public void setActive(boolean active) { 
        this.active = active; 
    }
    
    public void updateLastSeen() {
        this.lastSeen = System.currentTimeMillis();
    }
    
    public String getAddress() {
        return host + ":" + port;
    }
    
    @Override
    public String toString() {
        return id + "@" + host + ":" + port + (active ? " (active)" : " (inactive)");
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PeerInfo peerInfo = (PeerInfo) o;
        return port == peerInfo.port && 
               Objects.equals(id, peerInfo.id) && 
               Objects.equals(host, peerInfo.host);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id, host, port);
    }
}
