package net.argus;

import net.argus.tests.NetworkTest;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

public record TestResult(boolean success, long duration, LocalDateTime timestamp, String error) {}

enum MonitorStatus {
    OK, WARNING, FAILURE
}

class MonitorState {
    final Destination destination;
    final String groupName;
    private final Deque<TestResult> history = new ConcurrentLinkedDeque<>();
    private int consecutiveFailures = 0;
    private int consecutiveSuccesses = 0;
    private MonitorStatus currentStatus = MonitorStatus.OK;
    private volatile TestResult lastResult;
    private final NetworkTest testImplementation;
    private final String testDescription;

    public MonitorState(Destination destination, String groupName) {
        this.destination = destination;
        this.groupName = groupName;
        final var testConfigWithHost = new TestConfig(
              destination.test().testMethod()
            , destination.test().protocol()
            , destination.test().port()
            , destination.test().url()
            , destination.test().proxy()
            , destination.test().host()  // Add the host from destination
        );

        this.testImplementation = TestFactory.getTest(testConfigWithHost.testMethod());
        this.testDescription = TestFactory.validateAndDescribe(testConfigWithHost);
    }

    public synchronized void addResult(TestResult result) {
        this.lastResult = result;

        // Add to history and maintain size limit (cap at reasonable maximum)
        history.addLast(result);
        final var maxHistory = Math.min(destination.history(), 1000); // Cap at 1000 records max
        while (history.size() > maxHistory) {
            history.removeFirst();
        }

        // Update status based on consecutive results
        if (result.success()) {
            consecutiveSuccesses++;
            consecutiveFailures = 0;

            // Check if we should reset from warning/failure state
            if (currentStatus != MonitorStatus.OK && consecutiveSuccesses >= destination.reset()) {
                currentStatus = MonitorStatus.OK;
                consecutiveSuccesses = 0;
            }
        } else {
            consecutiveFailures++;
            consecutiveSuccesses = 0;

            // Update status based on failure thresholds
            if (consecutiveFailures >= destination.failure()) {
                currentStatus = MonitorStatus.FAILURE;
            } else if (consecutiveFailures >= destination.warning()) {
                currentStatus = MonitorStatus.WARNING;
            }
        }
    }

    public MonitorStatus getCurrentStatus() {
        return currentStatus;
    }

    public TestResult getLastResult() {
        return lastResult;
    }

    public double getUptimePercentage() {
        if (history.isEmpty()) {
            return 0.0;
        }

        final var successful = history.stream()
                .mapToLong(r -> r.success() ? 1 : 0)
                .sum();

        return (double) successful / history.size() * 100.0;
    }

    public List<TestResult> getHistory() {
        return new ArrayList<>(history);
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public int getConsecutiveSuccesses() {
        return consecutiveSuccesses;
    }

    public NetworkTest getTestImplementation() {
        return testImplementation;
    }

    public String getTestDescription() {
        return testDescription;
    }
}