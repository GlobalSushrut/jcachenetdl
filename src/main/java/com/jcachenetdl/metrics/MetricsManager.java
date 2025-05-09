package com.jcachenetdl.metrics;

import com.jcachenetdl.config.ConfigurationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages system metrics collection and reporting.
 */
public class MetricsManager {
    private static final Logger logger = LoggerFactory.getLogger(MetricsManager.class);
    private static MetricsManager instance;
    
    private final ConcurrentMap<String, AtomicLong> counters;
    private final ConcurrentMap<String, AtomicLong> gauges;
    private final ConcurrentMap<String, ConcurrentLinkedQueue<Long>> histograms;
    
    private final ScheduledExecutorService scheduler;
    private final ConfigurationManager config;
    private final String metricsDir;
    
    private MetricsManager() {
        this.counters = new ConcurrentHashMap<>();
        this.gauges = new ConcurrentHashMap<>();
        this.histograms = new ConcurrentHashMap<>();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.config = ConfigurationManager.getInstance();
        this.metricsDir = "metrics";
        
        // Ensure metrics directory exists
        File dir = new File(metricsDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        
        // Schedule metrics reporting
        if (config.getBoolean("metrics.enabled", true)) {
            int reportingInterval = config.getInt("metrics.reporting.interval.seconds", 60);
            scheduler.scheduleAtFixedRate(
                this::reportMetrics, 
                reportingInterval, 
                reportingInterval, 
                TimeUnit.SECONDS
            );
            logger.info("Metrics reporting scheduled every {} seconds", reportingInterval);
        }
    }
    
    /**
     * Gets the singleton instance of the MetricsManager.
     * 
     * @return The MetricsManager instance
     */
    public static synchronized MetricsManager getInstance() {
        if (instance == null) {
            instance = new MetricsManager();
        }
        return instance;
    }
    
    /**
     * Increments a counter metric.
     * 
     * @param name The metric name
     * @param value The amount to increment by
     */
    public void incrementCounter(String name, long value) {
        counters.computeIfAbsent(name, k -> new AtomicLong(0)).addAndGet(value);
    }
    
    /**
     * Sets a gauge metric.
     * 
     * @param name The metric name
     * @param value The gauge value
     */
    public void setGauge(String name, long value) {
        gauges.computeIfAbsent(name, k -> new AtomicLong(0)).set(value);
    }
    
    /**
     * Records a histogram value.
     * 
     * @param name The metric name
     * @param value The value to record
     */
    public void recordHistogram(String name, long value) {
        histograms.computeIfAbsent(name, k -> new ConcurrentLinkedQueue<>()).add(value);
        
        // Limit the size of the histogram to avoid memory issues
        ConcurrentLinkedQueue<Long> histogram = histograms.get(name);
        while (histogram.size() > 1000) {
            histogram.poll();
        }
    }
    
    /**
     * Gets the current value of a counter.
     * 
     * @param name The metric name
     * @return The counter value
     */
    public long getCounter(String name) {
        return counters.getOrDefault(name, new AtomicLong(0)).get();
    }
    
    /**
     * Gets the current value of a gauge.
     * 
     * @param name The metric name
     * @return The gauge value
     */
    public long getGauge(String name) {
        return gauges.getOrDefault(name, new AtomicLong(0)).get();
    }
    
    /**
     * Gets the percentile value of a histogram.
     * 
     * @param name The metric name
     * @param percentile The percentile (0-100)
     * @return The percentile value
     */
    public long getHistogramPercentile(String name, int percentile) {
        ConcurrentLinkedQueue<Long> histogram = histograms.get(name);
        if (histogram == null || histogram.isEmpty()) {
            return 0;
        }
        
        // Convert to array and sort
        Long[] values = histogram.toArray(new Long[0]);
        Arrays.sort(values);
        
        // Calculate percentile index
        int index = (int) Math.ceil(percentile / 100.0 * values.length) - 1;
        index = Math.max(0, Math.min(values.length - 1, index));
        
        return values[index];
    }
    
    /**
     * Reports all current metrics.
     */
    public void reportMetrics() {
        try {
            if (!config.getBoolean("metrics.enabled", true)) {
                return;
            }
            
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
            String filename = metricsDir + "/metrics_" + dateFormat.format(new Date()) + ".txt";
            
            try (FileWriter writer = new FileWriter(filename)) {
                writer.write("=== JCacheNetDL Metrics Report ===\n");
                writer.write("Generated: " + new Date() + "\n\n");
                
                // Write counters
                writer.write("=== Counters ===\n");
                for (Map.Entry<String, AtomicLong> entry : counters.entrySet()) {
                    writer.write(entry.getKey() + ": " + entry.getValue().get() + "\n");
                }
                writer.write("\n");
                
                // Write gauges
                writer.write("=== Gauges ===\n");
                for (Map.Entry<String, AtomicLong> entry : gauges.entrySet()) {
                    writer.write(entry.getKey() + ": " + entry.getValue().get() + "\n");
                }
                writer.write("\n");
                
                // Write histograms
                writer.write("=== Histograms ===\n");
                for (String name : histograms.keySet()) {
                    writer.write(name + ":\n");
                    writer.write("  p50: " + getHistogramPercentile(name, 50) + "\n");
                    writer.write("  p90: " + getHistogramPercentile(name, 90) + "\n");
                    writer.write("  p99: " + getHistogramPercentile(name, 99) + "\n");
                }
            }
            
            logger.debug("Metrics report written to: {}", filename);
        } catch (IOException e) {
            logger.error("Error writing metrics report", e);
        }
    }
    
    /**
     * Shuts down the metrics manager.
     */
    public void shutdown() {
        scheduler.shutdownNow();
        reportMetrics(); // Final report
    }
    
    /**
     * Gets all metrics as a map for API exposure.
     * 
     * @return Map containing all metrics
     */
    public Map<String, Object> getAllMetrics() {
        Map<String, Object> result = new ConcurrentHashMap<>();
        
        // Add counters
        Map<String, Long> countersMap = new ConcurrentHashMap<>();
        for (Map.Entry<String, AtomicLong> entry : counters.entrySet()) {
            countersMap.put(entry.getKey(), entry.getValue().get());
        }
        result.put("counters", countersMap);
        
        // Add gauges
        Map<String, Long> gaugesMap = new ConcurrentHashMap<>();
        for (Map.Entry<String, AtomicLong> entry : gauges.entrySet()) {
            gaugesMap.put(entry.getKey(), entry.getValue().get());
        }
        result.put("gauges", gaugesMap);
        
        // Add histogram percentiles
        Map<String, Map<String, Long>> histogramsMap = new ConcurrentHashMap<>();
        for (String name : histograms.keySet()) {
            Map<String, Long> percentiles = new ConcurrentHashMap<>();
            percentiles.put("p50", getHistogramPercentile(name, 50));
            percentiles.put("p90", getHistogramPercentile(name, 90));
            percentiles.put("p99", getHistogramPercentile(name, 99));
            histogramsMap.put(name, percentiles);
        }
        result.put("histograms", histogramsMap);
        
        return result;
    }
}
