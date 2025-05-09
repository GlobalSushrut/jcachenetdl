package com.jcachenetdl.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Manages application configuration with support for dynamic property changes.
 */
public class ConfigurationManager {
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationManager.class);
    private static final String CONFIG_FILE = "config/jcachenetdl.properties";
    private static ConfigurationManager instance;
    
    private final ConcurrentMap<String, String> configCache;
    private final Properties properties;
    
    private ConfigurationManager() {
        this.configCache = new ConcurrentHashMap<>();
        this.properties = new Properties();
        loadDefaultConfiguration();
        loadConfigurationFile();
    }
    
    /**
     * Gets the singleton instance of the ConfigurationManager.
     * 
     * @return The ConfigurationManager instance
     */
    public static synchronized ConfigurationManager getInstance() {
        if (instance == null) {
            instance = new ConfigurationManager();
        }
        return instance;
    }
    
    /**
     * Sets default configuration values.
     */
    private void loadDefaultConfiguration() {
        // Network defaults
        configCache.put("network.port", "8080");
        configCache.put("network.interface", "0.0.0.0");
        configCache.put("network.max.connections", "100");
        configCache.put("network.timeout.seconds", "30");
        
        // Cache defaults
        configCache.put("cache.dir", "cache");
        configCache.put("cache.max.size.mb", "1024"); // 1GB max cache
        configCache.put("cache.chunk.size.kb", "1024"); // 1MB chunk size
        configCache.put("cache.cleanup.interval.minutes", "60");
        configCache.put("cache.max.age.hours", "24");
        
        // Ledger defaults
        configCache.put("ledger.dir", "ledger/blocks");
        configCache.put("ledger.max.actions.per.block", "100");
        configCache.put("ledger.sync.interval.seconds", "60");
        
        // Security defaults
        configCache.put("security.enabled", "false");
        configCache.put("security.auth.token", "");
        configCache.put("security.encryption.enabled", "false");
        
        // Performance tuning
        configCache.put("performance.thread.pool.size", "10");
        configCache.put("performance.io.buffer.size.kb", "64");
        
        // Metrics
        configCache.put("metrics.enabled", "true");
        configCache.put("metrics.reporting.interval.seconds", "60");
    }
    
    /**
     * Loads configuration from file.
     */
    private void loadConfigurationFile() {
        File configFile = new File(CONFIG_FILE);
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                properties.load(fis);
                
                // Update cache with values from file
                for (String key : properties.stringPropertyNames()) {
                    configCache.put(key, properties.getProperty(key));
                }
                
                logger.info("Loaded configuration from file: {}", CONFIG_FILE);
            } catch (IOException e) {
                logger.error("Error loading configuration file: {}", e.getMessage());
            }
        } else {
            // Create config directory if it doesn't exist
            File configDir = configFile.getParentFile();
            if (!configDir.exists()) {
                configDir.mkdirs();
            }
            
            // Save default configuration
            try {
                saveConfiguration();
                logger.info("Created default configuration file: {}", CONFIG_FILE);
            } catch (IOException e) {
                logger.error("Error creating default configuration file: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Saves the current configuration to file.
     * 
     * @throws IOException If there's an error saving the configuration
     */
    public void saveConfiguration() throws IOException {
        // Update properties with values from cache
        for (String key : configCache.keySet()) {
            properties.setProperty(key, configCache.get(key));
        }
        
        // Save to file
        File configFile = new File(CONFIG_FILE);
        try (FileOutputStream fos = new FileOutputStream(configFile)) {
            properties.store(fos, "JCacheNetDL Configuration");
        }
        
        logger.info("Saved configuration to file: {}", CONFIG_FILE);
    }
    
    /**
     * Gets a string configuration value.
     * 
     * @param key The configuration key
     * @return The configuration value
     */
    public String getString(String key) {
        return configCache.getOrDefault(key, "");
    }
    
    /**
     * Gets a string configuration value with a default.
     * 
     * @param key The configuration key
     * @param defaultValue The default value
     * @return The configuration value
     */
    public String getString(String key, String defaultValue) {
        return configCache.getOrDefault(key, defaultValue);
    }
    
    /**
     * Gets an integer configuration value.
     * 
     * @param key The configuration key
     * @param defaultValue The default value
     * @return The configuration value
     */
    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(getString(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            logger.warn("Invalid integer value for key: {}", key);
            return defaultValue;
        }
    }
    
    /**
     * Gets a long configuration value.
     * 
     * @param key The configuration key
     * @param defaultValue The default value
     * @return The configuration value
     */
    public long getLong(String key, long defaultValue) {
        try {
            return Long.parseLong(getString(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            logger.warn("Invalid long value for key: {}", key);
            return defaultValue;
        }
    }
    
    /**
     * Gets a boolean configuration value.
     * 
     * @param key The configuration key
     * @param defaultValue The default value
     * @return The configuration value
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = getString(key, String.valueOf(defaultValue));
        return Boolean.parseBoolean(value);
    }
    
    /**
     * Sets a configuration value.
     * 
     * @param key The configuration key
     * @param value The configuration value
     */
    public void setValue(String key, String value) {
        configCache.put(key, value);
    }
    
    /**
     * Checks if a configuration key exists.
     * 
     * @param key The configuration key
     * @return True if the key exists
     */
    public boolean hasKey(String key) {
        return configCache.containsKey(key);
    }
    
    /**
     * Removes a configuration key.
     * 
     * @param key The configuration key
     */
    public void removeKey(String key) {
        configCache.remove(key);
    }
    
    /**
     * Reloads the configuration from file.
     */
    public void reload() {
        loadConfigurationFile();
        logger.info("Configuration reloaded");
    }
}
