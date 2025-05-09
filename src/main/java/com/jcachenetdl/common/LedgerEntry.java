package com.jcachenetdl.common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a block in the distributed ledger.
 */
public class LedgerEntry implements Serializable {
    private String blockId;
    private String previousHash;
    private long timestamp;
    private List<Action> actions;
    private String blockHash;
    private String creatorPeerId;
    private String signature; // Optional: for security layer

    public LedgerEntry(String blockId, String previousHash, String creatorPeerId) {
        this.blockId = blockId;
        this.previousHash = previousHash;
        this.timestamp = System.currentTimeMillis();
        this.actions = new ArrayList<>();
        this.creatorPeerId = creatorPeerId;
    }

    public String getBlockId() {
        return blockId;
    }

    public void setBlockId(String blockId) {
        this.blockId = blockId;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public void setPreviousHash(String previousHash) {
        this.previousHash = previousHash;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public List<Action> getActions() {
        return actions;
    }

    public void setActions(List<Action> actions) {
        this.actions = actions;
    }
    
    public void addAction(Action action) {
        this.actions.add(action);
    }

    public String getBlockHash() {
        return blockHash;
    }

    public void setBlockHash(String blockHash) {
        this.blockHash = blockHash;
    }
    
    public String getCreatorPeerId() {
        return creatorPeerId;
    }

    public void setCreatorPeerId(String creatorPeerId) {
        this.creatorPeerId = creatorPeerId;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LedgerEntry that = (LedgerEntry) o;
        return Objects.equals(blockId, that.blockId) && 
               Objects.equals(blockHash, that.blockHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(blockId, blockHash);
    }

    @Override
    public String toString() {
        return "LedgerEntry{" +
                "blockId='" + blockId + '\'' +
                ", previousHash='" + previousHash + '\'' +
                ", timestamp=" + timestamp +
                ", actionsCount=" + (actions != null ? actions.size() : 0) +
                ", blockHash='" + blockHash + '\'' +
                ", creatorPeerId='" + creatorPeerId + '\'' +
                '}';
    }
}
