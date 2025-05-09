package com.jcachenetdl.common;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents an action recorded in the distributed ledger.
 */
public class Action implements Serializable {
    private String type; // CACHE_PUT, CACHE_GET, CACHE_HIT
    private String fileHash;
    private String peerId;
    private long timestamp;
    private int chunkId;
    
    public Action(String type, String fileHash, String peerId, int chunkId) {
        this.type = type;
        this.fileHash = fileHash;
        this.peerId = peerId;
        this.timestamp = System.currentTimeMillis();
        this.chunkId = chunkId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public String getPeerId() {
        return peerId;
    }

    public void setPeerId(String peerId) {
        this.peerId = peerId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public int getChunkId() {
        return chunkId;
    }
    
    public void setChunkId(int chunkId) {
        this.chunkId = chunkId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Action action = (Action) o;
        return timestamp == action.timestamp &&
               chunkId == action.chunkId &&
               Objects.equals(type, action.type) &&
               Objects.equals(fileHash, action.fileHash) &&
               Objects.equals(peerId, action.peerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, fileHash, peerId, timestamp, chunkId);
    }

    @Override
    public String toString() {
        return "Action{" +
                "type='" + type + '\'' +
                ", fileHash='" + fileHash + '\'' +
                ", peerId='" + peerId + '\'' +
                ", chunkId=" + chunkId +
                ", timestamp=" + timestamp +
                '}';
    }
}
