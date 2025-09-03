package net.argus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class Monitors {
    private static final Logger logger = LoggerFactory.getLogger(Monitors.class);
    private final Map<String, MonitorState> monitors;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService testExecutor;

    Monitors(MonitorConfig config, Map<String, MonitorState> monitors, ScheduledExecutorService scheduler, ExecutorService testExecutor) {
        this.monitors = monitors;
        this.scheduler = scheduler;
        this.testExecutor = testExecutor;

        var totalMonitors = 0;
        for (var group : config.monitors()) {
            logger.info("Initializing monitor group: {}", group.group());
            for (var dest : group.destinations()) {
                try {
                    final var key = group.group() + ":" + dest.name();
                    final var state = new MonitorState(dest, group);
                    monitors.put(key, state);
                    totalMonitors++;
                    logger.debug("Initialized monitor: {} ({})", dest.name(), state.getTestDescription());
                } catch (Exception e) {
                    logger.error("Failed to initialize monitor {}: {}", dest.name(), e.getMessage());
                    throw new RuntimeException("Invalid test configuration for " + dest.name(), e);
                }
            }
        }
        logger.info("Initialized {} monitors across {} groups", totalMonitors, config.monitors().size());
    }

    void startMonitoring() {
        logger.info("Starting monitoring tasks");
        for (var entry : monitors.entrySet()) {
            final var state = entry.getValue();
            logger.debug("Scheduling monitor: {} (interval: {}s)", entry.getKey(), state.destination.interval());
            // Schedule periodic testing
            scheduler.scheduleAtFixedRate(
                    () -> performTest(state)
                    , 0
                    , state.destination.interval()
                    , TimeUnit.SECONDS
            );
        }
        logger.info("All monitoring tasks scheduled");
    }

    private void performTest(MonitorState state) {
        CompletableFuture.supplyAsync(() -> {
            final var result = executeTest(state);
            state.addResult(result);

            // Log significant status changes
            if (!result.success() && state.getCurrentStatus() != MonitorStatus.OK) {
                logger.warn("Monitor {} status: {} (consecutive failures: {})", state.destination.name(), state.getCurrentStatus(), state.getConsecutiveFailures());
            } else if (result.success() && state.getCurrentStatus() == MonitorStatus.OK && state.getConsecutiveSuccesses() == state.destination.reset()) {
                logger.info("Monitor {} recovered to OK status", state.destination.name());
            }
            return result;
        }, testExecutor).exceptionally(throwable -> {
            logger.error("Error executing test for {}: {}", state.destination.name(), throwable.getMessage(), throwable);
            return null;
        });
    }

    private TestResult executeTest(MonitorState state) {
        try {
            logger.trace("Executing {} test for {}", state.destination.test().testMethod(), state.destination.name());
            // Use the test implementation from the state
            final var result = state.getTestImplementation().execute(state.destination.test(), state.destination.timeout());
            logger.trace("Test {} for {} completed in {}ms: {}", state.destination.test().testMethod(), state.destination.name(), result.duration(), result.success() ? "SUCCESS" : "FAILURE");
            return result;
        } catch (Exception e) {
            logger.debug("Test failed for {} : {}", state.destination.name(), e.getMessage());
            return new TestResult(false, 0, LocalDateTime.now(), e.getMessage());
        }
    }
}
