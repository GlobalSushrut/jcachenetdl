package com.jcachenetdl.common;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

/**
 * Represents a cached file chunk in the system.
 */
public class CacheItem implements Serializable {
    private String fileHash;
    private int chunkId;
    private byte[] data;
    private long timestamp;
    private String ownerPeerId;
    private int totalChunks;

    public CacheItem(String fileHash, int chunkId, byte[] data, String ownerPeerId, int totalChunks) {
        this.fileHash = fileHash;
        this.chunkId = chunkId;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
        this.ownerPeerId = ownerPeerId;
        this.totalChunks = totalChunks;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public int getChunkId() {
        return chunkId;
    }

    public void setChunkId(int chunkId) {
        this.chunkId = chunkId;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getOwnerPeerId() {
        return ownerPeerId;
    }

    public void setOwnerPeerId(String ownerPeerId) {
        this.ownerPeerId = ownerPeerId;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(int totalChunks) {
        this.totalChunks = totalChunks;
    }

    public String getFileIdentifier() {
        return fileHash + "_" + chunkId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CacheItem cacheItem = (CacheItem) o;
        return chunkId == cacheItem.chunkId && 
               Objects.equals(fileHash, cacheItem.fileHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileHash, chunkId);
    }

    @Override
    public String toString() {
        return "CacheItem{" +
                "fileHash='" + fileHash + '\'' +
                ", chunkId=" + chunkId +
                ", dataSize=" + (data != null ? data.length : 0) +
                ", timestamp=" + timestamp +
                ", ownerPeerId='" + ownerPeerId + '\'' +
                ", totalChunks=" + totalChunks +
                '}';
    }
}
