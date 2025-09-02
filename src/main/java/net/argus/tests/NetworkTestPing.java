package net.argus.tests;

import net.argus.TestConfig;
import net.argus.TestResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.time.LocalDateTime;

public class NetworkTestPing implements NetworkTest {
    private static final Logger logger = LoggerFactory.getLogger(NetworkTestPing.class);

    @Override
    public TestResult execute(TestConfig config, int timeoutMs) {
        final var startTime = System.currentTimeMillis();
        var success = false;
        String error = null;

        try {
            validateConfig(config);
            final var address = InetAddress.getByName(config.host());
            success = address.isReachable(timeoutMs);

            if (!success) {
                error = "Host unreachable";
            }
        } catch (Exception e) {
            error = e.getMessage();
            logger.debug("Ping test failed for {}: {}", config.host(), error);
        }

        final var duration = System.currentTimeMillis() - startTime;
        return new TestResult(success, duration, LocalDateTime.now(), error);
    }

    @Override
    public String getDescription(TestConfig config) {
        return "PING";
    }

    @Override
    public void validateConfig(TestConfig config) {
        if (config.host() == null || config.host().trim().isEmpty()) {
            throw new IllegalArgumentException("Host is required for ping test");
        }
    }
}
