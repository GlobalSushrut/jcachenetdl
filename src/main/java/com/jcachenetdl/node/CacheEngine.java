package com.jcachenetdl.node;

import com.jcachenetdl.common.CacheItem;
import com.jcachenetdl.ledger.ActionType;
import com.jcachenetdl.ledger.DistributedLedger;
import com.jcachenetdl.util.HashUtil;
import com.jcachenetdl.util.LogUtil;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Manages the file cache operations.
 */
public class CacheEngine {
    private static final String CACHE_DIR = "cache";
    private static final int MAX_CHUNK_SIZE = 1024 * 1024; // 1MB
    
    private final String peerId;
    private final DistributedLedger ledger;
    private final Map<String, CacheItem> cache; // In-memory cache (fileHash_chunkId -> CacheItem)
    private final Logger logger;
    private final Map<String, String> simpleCache = new ConcurrentHashMap<>();

    public CacheEngine(String peerId, DistributedLedger ledger) {
        this.peerId = peerId;
        this.ledger = ledger;
        this.cache = new ConcurrentHashMap<>();
        this.logger = LogUtil.getLogger(CacheEngine.class);
        
        // Ensure cache directory exists
        Path dirPath = Paths.get(CACHE_DIR);
        if (!Files.exists(dirPath)) {
            try {
                Files.createDirectories(dirPath);
            } catch (IOException e) {
                logger.error("Failed to create cache directory", e);
            }
        }
        
        // Load cache from disk
        loadCacheFromDisk();
    }
    
    /**
     * Loads cached items from disk.
     */
    private void loadCacheFromDisk() {
        File cacheDir = new File(CACHE_DIR);
        File[] cacheFiles = cacheDir.listFiles((dir, name) -> name.endsWith(".zip"));
        
        if (cacheFiles != null) {
            for (File file : cacheFiles) {
                try {
                    String fileName = file.getName();
                    String[] parts = fileName.substring(0, fileName.lastIndexOf('.')).split("_");
                    if (parts.length == 2) {
                        String fileHash = parts[0];
                        int chunkId = Integer.parseInt(parts[1]);
                        
                        // Load the file content
                        byte[] compressedData = Files.readAllBytes(file.toPath());
                        byte[] data = unzipData(compressedData);
                        
                        // Create cache item (we don't know the total chunks here, will be updated later)
                        CacheItem item = new CacheItem(fileHash, chunkId, data, peerId, 1);
                        cache.put(getCacheKey(fileHash, chunkId), item);
                        logger.debug("Loaded cache item from disk: {}_{}", fileHash, chunkId);
                    }
                } catch (Exception e) {
                    logger.error("Error loading cache item: {}", file.getName(), e);
                }
            }
            logger.info("Loaded {} cache items from disk", cache.size());
        }
    }
    
    /**
     * Gets a cache item key.
     * 
     * @param fileHash The file hash
     * @param chunkId The chunk ID
     * @return The cache key
     */
    private String getCacheKey(String fileHash, int chunkId) {
        return fileHash + "_" + chunkId;
    }
    
    /**
     * Puts a file chunk into the cache.
     * 
     * @param fileHash The file hash
     * @param chunkId The chunk ID
     * @param data The chunk data
     * @param totalChunks The total number of chunks
     * @return True if the operation was successful
     */
    public boolean putCacheItem(String fileHash, int chunkId, byte[] data, int totalChunks) {
        try {
            // Create cache item
            CacheItem item = new CacheItem(fileHash, chunkId, data, peerId, totalChunks);
            String cacheKey = getCacheKey(fileHash, chunkId);
            
            // Store in memory
            cache.put(cacheKey, item);
            
            // Save to disk
            saveCacheItemToDisk(item);
            
            // Log to ledger
            ledger.addAction(ledger.createAction(ActionType.CACHE_PUT, fileHash, chunkId));
            
            logger.debug("Added cache item: {}_{}", fileHash, chunkId);
            LogUtil.logCachePut(fileHash, chunkId);
            
            return true;
        } catch (Exception e) {
            logger.error("Error putting cache item: {}_{}", fileHash, chunkId, e);
            return false;
        }
    }
    
    /**
     * Gets a file chunk from the cache.
     * 
     * @param fileHash The file hash
     * @param chunkId The chunk ID
     * @return The cache item, or null if not found
     */
    public CacheItem getCacheItem(String fileHash, int chunkId) {
        String cacheKey = getCacheKey(fileHash, chunkId);
        CacheItem item = cache.get(cacheKey);
        
        if (item != null) {
            // Log to ledger
            ledger.addAction(ledger.createAction(ActionType.CACHE_HIT, fileHash, chunkId));
            logger.debug("Cache hit: {}_{}", fileHash, chunkId);
            LogUtil.logCacheHit(fileHash, chunkId);
        }
        
        return item;
    }
    
    /**
     * Saves a cache item to disk.
     * 
     * @param item The cache item to save
     * @throws IOException If there's an error saving the item
     */
    private void saveCacheItemToDisk(CacheItem item) throws IOException {
        String fileName = item.getFileHash() + "_" + item.getChunkId() + ".zip";
        File file = new File(CACHE_DIR, fileName);
        
        // Compress the data
        byte[] compressedData = zipData(item.getData());
        
        // Write to disk
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(compressedData);
        }
    }
    
    /**
     * Compresses data using ZIP.
     * 
     * @param data The data to compress
     * @return The compressed data
     * @throws IOException If there's an error compressing the data
     */
    private byte[] zipData(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry("data");
            entry.setSize(data.length);
            zos.putNextEntry(entry);
            zos.write(data);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
    
    /**
     * Decompresses data using ZIP.
     * 
     * @param compressedData The compressed data
     * @return The decompressed data
     * @throws IOException If there's an error decompressing the data
     */
    private byte[] unzipData(byte[] compressedData) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        try (ZipInputStream zis = new ZipInputStream(bais)) {
            ZipEntry entry = zis.getNextEntry();
            if (entry != null) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    baos.write(buffer, 0, len);
                }
            }
        }
        
        return baos.toByteArray();
    }
    
    /**
     * Splits a file into chunks and caches them.
     * 
     * @param file The file to split and cache
     * @return The file hash of the cached file
     * @throws IOException If there's an error processing the file
     */
    public String cacheFile(File file) throws IOException {
        // Calculate file hash
        String fileHash = HashUtil.sha256File(file);
        
        // Read the file
        byte[] fileData = Files.readAllBytes(file.toPath());
        int totalSize = fileData.length;
        
        // Calculate number of chunks
        int numChunks = (int) Math.ceil((double) totalSize / MAX_CHUNK_SIZE);
        
        // Split into chunks and cache
        for (int i = 0; i < numChunks; i++) {
            int start = i * MAX_CHUNK_SIZE;
            int end = Math.min(start + MAX_CHUNK_SIZE, totalSize);
            byte[] chunkData = Arrays.copyOfRange(fileData, start, end);
            
            putCacheItem(fileHash, i, chunkData, numChunks);
        }
        
        logger.info("File cached: {}, size: {}, chunks: {}", fileHash, totalSize, numChunks);
        return fileHash;
    }
    
    /**
     * Recreates a complete file from cached chunks.
     * 
     * @param fileHash The file hash
     * @param outputFile The output file
     * @return True if the file was successfully recreated
     * @throws IOException If there's an error recreating the file
     */
    public boolean recreateFile(String fileHash, File outputFile) throws IOException {
        // Find any chunk to get total chunks info
        CacheItem anyChunk = null;
        for (CacheItem item : cache.values()) {
            if (item.getFileHash().equals(fileHash)) {
                anyChunk = item;
                break;
            }
        }
        
        if (anyChunk == null) {
            logger.error("No chunks found for file: {}", fileHash);
            return false;
        }
        
        int totalChunks = anyChunk.getTotalChunks();
        
        // Check if we have all chunks
        for (int i = 0; i < totalChunks; i++) {
            if (getCacheItem(fileHash, i) == null) {
                logger.error("Missing chunk {} of {} for file: {}", i, totalChunks, fileHash);
                return false;
            }
        }
        
        // Recreate the file
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            for (int i = 0; i < totalChunks; i++) {
                CacheItem chunk = getCacheItem(fileHash, i);
                fos.write(chunk.getData());
                
                // Log to ledger
                ledger.addAction(ledger.createAction(ActionType.CACHE_GET, fileHash, i));
                LogUtil.logCacheGet(fileHash, i);
            }
        }
        
        logger.info("File recreated: {} with {} chunks", fileHash, totalChunks);
        return true;
    }
    
    /**
     * Gets the cache size (number of chunks).
     * 
     * @return The cache size
     */
    public int getCacheSize() {
        return cache.size();
    }
    
    /**
     * Cleans the cache by removing items older than the specified time.
     * 
     * @param maxAgeMs The maximum age in milliseconds
     * @return The number of items removed
     */
    public int cleanCache(long maxAgeMs) {
        int removed = 0;
        long now = System.currentTimeMillis();
        
        for (Map.Entry<String, CacheItem> entry : cache.entrySet()) {
            CacheItem item = entry.getValue();
            if (now - item.getTimestamp() > maxAgeMs) {
                String cacheKey = entry.getKey();
                cache.remove(cacheKey);
                
                // Remove from disk
                String fileName = item.getFileHash() + "_" + item.getChunkId() + ".zip";
                File file = new File(CACHE_DIR, fileName);
                if (file.exists()) {
                    file.delete();
                }
                
                removed++;
            }
        }
        
        if (removed > 0) {
            logger.info("Cleaned {} cache items", removed);
        }
        
        return removed;
    }

    /**
     * Adds a simple key-value item to the cache.
     * 
     * @param key The cache key
     * @param value The cache value
     */
    public void addItem(String key, String value) {
        simpleCache.put(key, value);
    }

    /**
     * Lists all simple key-value items in the cache.
     * 
     * @return A map of key-value pairs
     */
    public Map<String, String> listItems() {
        return new HashMap<>(simpleCache);
    }
}
