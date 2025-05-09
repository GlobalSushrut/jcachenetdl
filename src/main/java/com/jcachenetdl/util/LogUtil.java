package com.jcachenetdl.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Utility class for logging operations.
 */
public class LogUtil {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    private static final String LOG_DIR = "logs";
    private static final String LOG_FILE_FORMAT = "yyyy-MM-dd";
    
    /**
     * Get a logger for the specified class.
     * 
     * @param clazz The class to get the logger for
     * @return The logger
     */
    public static Logger getLogger(Class<?> clazz) {
        return LoggerFactory.getLogger(clazz);
    }
    
    /**
     * Initializes the logging system.
     */
    public static void init() {
        File logDir = new File(LOG_DIR);
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
    }
    
    /**
     * Logs a message to the file system.
     * 
     * @param type The log type (e.g., PEER_JOIN, CACHE_HIT)
     * @param message The log message
     */
    public static void logToFile(String type, String message) {
        String dateStr = new SimpleDateFormat(LOG_FILE_FORMAT).format(new Date());
        File logFile = new File(LOG_DIR, dateStr + ".log");
        
        try {
            if (!logFile.exists()) {
                logFile.getParentFile().mkdirs();
                logFile.createNewFile();
            }
            
            try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
                String timestamp = DATE_FORMAT.format(new Date());
                String logLine = String.format("[%s] [%s] %s", timestamp, type, message);
                writer.println(logLine);
                
                // Also print to console
                System.out.println(logLine);
            }
        } catch (IOException e) {
            System.err.println("Error writing to log file: " + e.getMessage());
        }
    }
    
    /**
     * Logs a peer join event.
     * 
     * @param peerId The ID of the joining peer
     */
    public static void logPeerJoin(String peerId) {
        logToFile("PEER_JOIN", "PeerId: " + peerId);
    }
    
    /**
     * Logs a cache hit event.
     * 
     * @param fileHash The hash of the file
     * @param chunkId The chunk ID
     */
    public static void logCacheHit(String fileHash, int chunkId) {
        logToFile("CACHE_HIT", String.format("File: %s Chunk: %d", fileHash, chunkId));
    }
    
    /**
     * Logs a cache put event.
     * 
     * @param fileHash The hash of the file
     * @param chunkId The chunk ID
     */
    public static void logCachePut(String fileHash, int chunkId) {
        logToFile("CACHE_PUT", String.format("File: %s Chunk: %d", fileHash, chunkId));
    }
    
    /**
     * Logs a cache get event.
     * 
     * @param fileHash The hash of the file
     * @param chunkId The chunk ID
     */
    public static void logCacheGet(String fileHash, int chunkId) {
        logToFile("CACHE_GET", String.format("File: %s Chunk: %d", fileHash, chunkId));
    }
    
    /**
     * Logs a ledger sync event.
     * 
     * @param blocksCount The number of blocks synced
     */
    public static void logLedgerSync(int blocksCount) {
        logToFile("LEDGER_SYNC", String.format("Synced %d blocks", blocksCount));
    }
}
