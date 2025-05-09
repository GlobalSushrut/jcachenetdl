package com.jcachenetdl.ledger;

import com.jcachenetdl.common.Action;
import com.jcachenetdl.common.LedgerEntry;
import com.jcachenetdl.util.HashUtil;
import com.jcachenetdl.util.LogUtil;
import com.jcachenetdl.util.SerializationUtil;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Manages the distributed ledger functionality.
 */
public class DistributedLedger {
    private static final int MAX_ACTIONS_PER_BLOCK = 100;
    private static final String LEDGER_DIR = "ledger/blocks";
    
    private final String peerId;
    private final List<LedgerEntry> chain;
    private LedgerEntry currentBlock;
    private final ReadWriteLock lock;
    private final Logger logger;
    
    public DistributedLedger(String peerId) {
        this.peerId = peerId;
        this.chain = new CopyOnWriteArrayList<>();
        this.lock = new ReentrantReadWriteLock();
        this.logger = LogUtil.getLogger(DistributedLedger.class);
        
        // Ensure ledger directory exists
        Path dirPath = Paths.get(LEDGER_DIR);
        if (!Files.exists(dirPath)) {
            try {
                Files.createDirectories(dirPath);
            } catch (IOException e) {
                logger.error("Failed to create ledger directory", e);
            }
        }
        
        // Try to load existing ledger
        loadLedger();
        
        // If ledger is empty, create genesis block
        if (chain.isEmpty()) {
            createGenesisBlock();
        }
        
        // Initialize current block
        String lastBlockHash = chain.get(chain.size() - 1).getBlockHash();
        String blockId = UUID.randomUUID().toString();
        this.currentBlock = new LedgerEntry(blockId, lastBlockHash, peerId);
    }
    
    /**
     * Creates the genesis block (first block in the chain).
     */
    private void createGenesisBlock() {
        String genesisBlockId = "0";
        LedgerEntry genesis = new LedgerEntry(genesisBlockId, "0", peerId);
        genesis.setBlockHash(calculateBlockHash(genesis));
        chain.add(genesis);
        try {
            saveBlock(genesis);
        } catch (IOException e) {
            logger.error("Failed to save genesis block", e);
        }
    }
    
    /**
     * Loads the ledger from disk.
     */
    private void loadLedger() {
        File blockDir = new File(LEDGER_DIR);
        if (!blockDir.exists()) {
            return;
        }
        
        File[] blockFiles = blockDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (blockFiles == null || blockFiles.length == 0) {
            return;
        }
        
        Arrays.sort(blockFiles, Comparator.comparing(File::getName));
        
        for (File blockFile : blockFiles) {
            try {
                LedgerEntry block = SerializationUtil.deserializeFromFile(blockFile, LedgerEntry.class);
                if (validateBlock(block)) {
                    chain.add(block);
                } else {
                    logger.warn("Invalid block found: {}", blockFile.getName());
                }
            } catch (IOException e) {
                logger.error("Failed to load block: {}", blockFile.getName(), e);
            }
        }
    }
    
    /**
     * Adds an action to the current block.
     * 
     * @param action The action to add
     * @return True if a new block was created as a result of this action
     */
    public boolean addAction(Action action) {
        lock.writeLock().lock();
        try {
            currentBlock.addAction(action);
            
            // Log the action based on its type
            switch (ActionType.fromValue(action.getType())) {
                case CACHE_PUT:
                    LogUtil.logCachePut(action.getFileHash(), action.getChunkId());
                    break;
                case CACHE_GET:
                    LogUtil.logCacheGet(action.getFileHash(), action.getChunkId());
                    break;
                case CACHE_HIT:
                    LogUtil.logCacheHit(action.getFileHash(), action.getChunkId());
                    break;
            }
            
            // Check if current block should be sealed
            if (currentBlock.getActions().size() >= MAX_ACTIONS_PER_BLOCK) {
                sealCurrentBlock();
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Seals the current block and starts a new one.
     * 
     * @return The sealed block
     */
    public LedgerEntry sealCurrentBlock() {
        lock.writeLock().lock();
        try {
            if (currentBlock.getActions().isEmpty()) {
                return null;
            }
            
            // Calculate block hash
            String blockHash = calculateBlockHash(currentBlock);
            currentBlock.setBlockHash(blockHash);
            
            // Add to chain
            chain.add(currentBlock);
            
            // Save to disk
            try {
                saveBlock(currentBlock);
            } catch (IOException e) {
                logger.error("Failed to save block", e);
            }
            
            // Create new block
            LedgerEntry sealedBlock = currentBlock;
            String blockId = UUID.randomUUID().toString();
            currentBlock = new LedgerEntry(blockId, sealedBlock.getBlockHash(), peerId);
            
            logger.info("Block sealed: {} with {} actions", sealedBlock.getBlockId(), sealedBlock.getActions().size());
            return sealedBlock;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Calculates the hash for a block.
     * 
     * @param block The block to hash
     * @return The hash string
     */
    private String calculateBlockHash(LedgerEntry block) {
        StringBuilder data = new StringBuilder();
        data.append(block.getBlockId())
            .append(block.getPreviousHash())
            .append(block.getTimestamp());
        
        // Add all actions to the hash
        for (Action action : block.getActions()) {
            data.append(action.getType())
                .append(action.getFileHash())
                .append(action.getPeerId())
                .append(action.getTimestamp())
                .append(action.getChunkId());
        }
        
        return HashUtil.sha256(data.toString());
    }
    
    /**
     * Saves a block to disk.
     * 
     * @param block The block to save
     * @throws IOException If there's an error saving the block
     */
    private void saveBlock(LedgerEntry block) throws IOException {
        File blockFile = new File(LEDGER_DIR, block.getBlockId() + ".json");
        SerializationUtil.serializeToFile(block, blockFile);
    }
    
    /**
     * Validates a block to ensure its hash is correct and its previous hash links correctly.
     * 
     * @param block The block to validate
     * @return True if the block is valid
     */
    public boolean validateBlock(LedgerEntry block) {
        // Check block hash
        String calculatedHash = calculateBlockHash(block);
        if (!calculatedHash.equals(block.getBlockHash())) {
            logger.warn("Block hash doesn't match calculated hash: {}", block.getBlockId());
            return false;
        }
        
        // Check previous hash link (except for genesis)
        if (!block.getBlockId().equals("0")) {
            LedgerEntry previousBlock = null;
            for (LedgerEntry entry : chain) {
                if (entry.getBlockHash().equals(block.getPreviousHash())) {
                    previousBlock = entry;
                    break;
                }
            }
            
            if (previousBlock == null) {
                logger.warn("Block has no valid previous block: {}", block.getBlockId());
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Validates the entire chain.
     * 
     * @return True if the chain is valid
     */
    public boolean validateChain() {
        lock.readLock().lock();
        try {
            if (chain.isEmpty()) {
                return true;
            }
            
            for (int i = 0; i < chain.size(); i++) {
                LedgerEntry block = chain.get(i);
                
                // Validate block hash
                if (!validateBlock(block)) {
                    return false;
                }
                
                // Check linking (except genesis block)
                if (i > 0) {
                    LedgerEntry previousBlock = chain.get(i - 1);
                    if (!block.getPreviousHash().equals(previousBlock.getBlockHash())) {
                        logger.warn("Chain broken at block: {}", block.getBlockId());
                        return false;
                    }
                }
            }
            return true;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets the blocks after the specified block hash.
     * 
     * @param sinceBlockHash The hash to start from (exclusive)
     * @return The list of blocks after the specified hash
     */
    public List<LedgerEntry> getBlocksSince(String sinceBlockHash) {
        lock.readLock().lock();
        try {
            int startIndex = -1;
            
            // Find the index of the block with the given hash
            for (int i = 0; i < chain.size(); i++) {
                if (chain.get(i).getBlockHash().equals(sinceBlockHash)) {
                    startIndex = i;
                    break;
                }
            }
            
            if (startIndex == -1) {
                // Hash not found, return all blocks
                return new ArrayList<>(chain);
            }
            
            // Return all blocks after the one with the given hash
            return new ArrayList<>(chain.subList(startIndex + 1, chain.size()));
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Adds a block to the chain.
     * 
     * @param block The block to add
     * @return True if the block was added successfully
     */
    public boolean addBlock(LedgerEntry block) {
        lock.writeLock().lock();
        try {
            // Check if block already exists
            for (LedgerEntry existingBlock : chain) {
                if (existingBlock.getBlockId().equals(block.getBlockId())) {
                    logger.debug("Block already exists: {}", block.getBlockId());
                    return false;
                }
            }
            
            // Validate the block
            if (!validateBlock(block)) {
                logger.warn("Invalid block received: {}", block.getBlockId());
                return false;
            }
            
            // Add to chain
            chain.add(block);
            
            // Save to disk
            try {
                saveBlock(block);
            } catch (IOException e) {
                logger.error("Failed to save block", e);
                chain.remove(block);
                return false;
            }
            
            logger.info("Block added to chain: {}", block.getBlockId());
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Gets the last block in the chain.
     * 
     * @return The last block
     */
    public LedgerEntry getLastBlock() {
        lock.readLock().lock();
        try {
            if (chain.isEmpty()) {
                return null;
            }
            return chain.get(chain.size() - 1);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets the current block being built.
     * 
     * @return The current block
     */
    public LedgerEntry getCurrentBlock() {
        lock.readLock().lock();
        try {
            return currentBlock;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets the chain size.
     * 
     * @return The number of blocks in the chain
     */
    public int getChainSize() {
        lock.readLock().lock();
        try {
            return chain.size();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Creates an action of the specified type.
     * 
     * @param type The action type
     * @param fileHash The file hash
     * @param chunkId The chunk ID
     * @return The created action
     */
    public Action createAction(ActionType type, String fileHash, int chunkId) {
        return new Action(type.getValue(), fileHash, peerId, chunkId);
    }
}
