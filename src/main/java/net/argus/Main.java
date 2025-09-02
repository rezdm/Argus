package net.argus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private final Map<String, MonitorState> monitorsMap = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final ExecutorService testExecutor = Executors.newFixedThreadPool(4);
    private WebServer server = null;

    public Main(String configPath) throws Exception {
        logger.info("Starting Argus Monitor with config: {}", configPath);
        logMemoryUsage("Startup");

        MonitorConfig config = MonitorConfig.loadConfig(configPath);
        logger.info("Loaded configuration for instance: {}", config.name());
        logMemoryUsage("Config loaded");

        Monitors monitors = new Monitors(config, monitorsMap, scheduler, testExecutor);
        logMemoryUsage("Monitors initialized");

        server = new WebServer(config, monitorsMap);
        monitors.startMonitoring();
        logger.info("Argus Monitor initialization complete");
        logMemoryUsage("Fully started");

        // Schedule periodic memory logging
        scheduler.scheduleAtFixedRate(() -> logMemoryUsage("Runtime"),60, 300, TimeUnit.SECONDS);
    }

    private void logMemoryUsage(String phase) {
        final var runtime = Runtime.getRuntime();
        final var totalMB = runtime.totalMemory() / 1024 / 1024;
        final var freeMB = runtime.freeMemory() / 1024 / 1024;
        final var usedMB = totalMB - freeMB;
        final var maxMB = runtime.maxMemory() / 1024 / 1024;

        logger.info("Memory [{}]: Used={}MB, Free={}MB, Total={}MB, Max={}MB", phase, usedMB, freeMB, totalMB, maxMB);
    }

    public void shutdown() {
        logger.info("Shutting down Argus Monitor");

        if (server != null) {
            server.stop();
        }

        scheduler.shutdown();
        testExecutor.shutdown();

        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
                logger.warn("Scheduler forced shutdown");
            }
            if (!testExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                testExecutor.shutdownNow();
                logger.warn("Test executor forced shutdown");
            }
        } catch (InterruptedException e) {
            logger.error("Interrupted during shutdown", e);
            Thread.currentThread().interrupt();
        }

        logger.info("Argus Monitor shutdown complete");
    }

    static void main(String[] args) {
        if (args.length != 1) {
            logger.error("Usage: java ArgusMonitor <config.json>");
            System.err.println("Usage: java ArgusMonitor <config.json>");
            System.exit(1);
        }

        // Set process ID for logging
        final var pid = String.valueOf(ProcessHandle.current().pid());
        System.setProperty("PID", pid);

        try {
            logger.info("Starting Argus Monitor version 1.0.0");
            final var monitor = new Main(args[0]);

            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Received shutdown signal");
                monitor.shutdown();
            }));

            logger.info("Argus Monitor started successfully. Press Ctrl+C to stop.");

            // Keep the main thread alive
            Thread.currentThread().join();
        } catch (Exception e) {
            logger.error("Error starting Argus Monitor: {}", e.getMessage(), e);
            System.err.println("Error starting Argus Monitor: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}