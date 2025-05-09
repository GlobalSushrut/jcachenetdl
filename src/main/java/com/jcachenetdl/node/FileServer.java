package com.jcachenetdl.node;

import com.jcachenetdl.common.CacheItem;
import com.jcachenetdl.common.PeerInfo;
import com.jcachenetdl.ledger.ActionType;
import com.jcachenetdl.ledger.DistributedLedger;
import com.jcachenetdl.network.PeerDiscovery;
import com.jcachenetdl.network.PeerHandler;
import com.jcachenetdl.util.LogUtil;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Handles file serving operations, including fetching files from remote peers.
 */
public class FileServer {
    private final String peerId;
    private final CacheEngine cacheEngine;
    private final PeerDiscovery peerDiscovery;
    private final PeerHandler peerHandler;
    private final DistributedLedger ledger;
    private final ExecutorService executor;
    private final Logger logger;
    
    public FileServer(String peerId, CacheEngine cacheEngine, PeerDiscovery peerDiscovery, 
                     PeerHandler peerHandler, DistributedLedger ledger) {
        this.peerId = peerId;
        this.cacheEngine = cacheEngine;
        this.peerDiscovery = peerDiscovery;
        this.peerHandler = peerHandler;
        this.ledger = ledger;
        this.executor = Executors.newFixedThreadPool(10);
        this.logger = LogUtil.getLogger(FileServer.class);
    }
    
    /**
     * Uploads a file to the P2P network (splits into chunks and caches locally).
     * 
     * @param file The file to upload
     * @return The file hash of the uploaded file
     * @throws IOException If there's an error uploading the file
     */
    public String uploadFile(File file) throws IOException {
        if (!file.exists() || !file.isFile()) {
            throw new IOException("File does not exist or is not a regular file: " + file.getAbsolutePath());
        }
        
        logger.info("Uploading file: {}", file.getName());
        return cacheEngine.cacheFile(file);
    }
    
    /**
     * Fetches a file from the P2P network.
     * 
     * @param fileHash The hash of the file to fetch
     * @param outputFile The output file to write to
     * @return True if the file was successfully fetched
     * @throws IOException If there's an error fetching the file
     */
    public boolean fetchFile(String fileHash, File outputFile) throws IOException {
        logger.info("Fetching file: {}", fileHash);
        
        // First try to recreate from local cache
        try {
            if (cacheEngine.recreateFile(fileHash, outputFile)) {
                logger.info("File recreated from local cache: {}", fileHash);
                return true;
            }
        } catch (IOException e) {
            logger.debug("Could not recreate file from local cache: {}", e.getMessage());
            // Continue to fetch from peers
        }
        
        // Need to fetch from peers
        return fetchFileFromPeers(fileHash, outputFile);
    }
    
    /**
     * Fetches a file from peers in the network.
     * 
     * @param fileHash The hash of the file to fetch
     * @param outputFile The output file to write to
     * @return True if the file was successfully fetched
     * @throws IOException If there's an error fetching the file
     */
    private boolean fetchFileFromPeers(String fileHash, File outputFile) throws IOException {
        List<PeerInfo> activePeers = peerDiscovery.getActivePeers();
        if (activePeers.isEmpty()) {
            logger.error("No active peers to fetch file from");
            return false;
        }
        
        // First, determine the total number of chunks by fetching chunk 0
        CacheItem firstChunk = null;
        for (PeerInfo peer : activePeers) {
            firstChunk = peerHandler.requestFileChunk(peer, fileHash, 0);
            if (firstChunk != null) {
                break;
            }
        }
        
        if (firstChunk == null) {
            logger.error("Failed to fetch first chunk of file: {}", fileHash);
            return false;
        }
        
        int totalChunks = firstChunk.getTotalChunks();
        logger.info("Fetching file with {} chunks", totalChunks);
        
        // Cache the first chunk
        cacheEngine.putCacheItem(fileHash, 0, firstChunk.getData(), totalChunks);
        
        // Create a map to track which peer has which chunk
        Map<Integer, List<PeerInfo>> chunkPeerMap = new ConcurrentHashMap<>();
        
        // Fetch the remaining chunks in parallel
        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 1; i < totalChunks; i++) {
            final int chunkId = i;
            futures.add(executor.submit(() -> {
                for (PeerInfo peer : activePeers) {
                    CacheItem chunk = peerHandler.requestFileChunk(peer, fileHash, chunkId);
                    if (chunk != null) {
                        cacheEngine.putCacheItem(fileHash, chunkId, chunk.getData(), totalChunks);
                        
                        // Track which peer has this chunk
                        chunkPeerMap.computeIfAbsent(chunkId, k -> new ArrayList<>()).add(peer);
                        
                        logger.debug("Fetched chunk {} from {}", chunkId, peer);
                        return true;
                    }
                }
                logger.error("Failed to fetch chunk {}", chunkId);
                return false;
            }));
        }
        
        // Wait for all chunks to be fetched
        boolean allChunksFetched = true;
        for (Future<Boolean> future : futures) {
            try {
                allChunksFetched &= future.get();
            } catch (Exception e) {
                logger.error("Error fetching chunk", e);
                allChunksFetched = false;
            }
        }
        
        if (!allChunksFetched) {
            logger.error("Failed to fetch all chunks of file: {}", fileHash);
            return false;
        }
        
        // Recreate the file from the fetched chunks
        return cacheEngine.recreateFile(fileHash, outputFile);
    }
    
    /**
     * Gets information about a file in the P2P network.
     * 
     * @param fileHash The hash of the file
     * @return Map containing file information, or null if not found
     */
    public Map<String, Object> getFileInfo(String fileHash) {
        // Check for any chunk of this file
        CacheItem anyChunk = null;
        
        // Try all chunks from 0 to a reasonable number
        for (int i = 0; i < 100; i++) {
            CacheItem chunk = cacheEngine.getCacheItem(fileHash, i);
            if (chunk != null) {
                anyChunk = chunk;
                break;
            }
        }
        
        if (anyChunk == null) {
            // Try to fetch chunk 0 from peers
            List<PeerInfo> activePeers = peerDiscovery.getActivePeers();
            for (PeerInfo peer : activePeers) {
                anyChunk = peerHandler.requestFileChunk(peer, fileHash, 0);
                if (anyChunk != null) {
                    break;
                }
            }
        }
        
        if (anyChunk == null) {
            return null;
        }
        
        // Build file info
        Map<String, Object> info = new HashMap<>();
        info.put("fileHash", fileHash);
        info.put("totalChunks", anyChunk.getTotalChunks());
        info.put("estimatedSize", anyChunk.getTotalChunks() * anyChunk.getData().length); // rough estimate
        info.put("chunksLocally", countLocalChunks(fileHash, anyChunk.getTotalChunks()));
        
        return info;
    }
    
    /**
     * Counts how many chunks of a file are stored locally.
     * 
     * @param fileHash The hash of the file
     * @param totalChunks The total number of chunks
     * @return The number of chunks stored locally
     */
    private int countLocalChunks(String fileHash, int totalChunks) {
        int count = 0;
        for (int i = 0; i < totalChunks; i++) {
            if (cacheEngine.getCacheItem(fileHash, i) != null) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Stops the file server.
     */
    public void shutdown() {
        executor.shutdownNow();
        logger.info("File server shutdown");
    }
}
