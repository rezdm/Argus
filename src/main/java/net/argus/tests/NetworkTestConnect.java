package net.argus.tests;

import net.argus.TestConfig;
import net.argus.TestResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.time.LocalDateTime;

public class NetworkTestConnect implements NetworkTest {
    private static final Logger logger = LoggerFactory.getLogger(NetworkTestConnect.class);

    @Override
    public TestResult execute(TestConfig config, int timeoutMs) {
        final var startTime = System.currentTimeMillis();
        var success = false;
        String error = null;

        try {
            validateConfig(config);

            success = switch (config.protocol()) {
                case TCP -> testTcpConnection(config.host(), config.port(), timeoutMs);
                case UDP -> testUdpConnection(config.host(), config.port(), timeoutMs);
                case null, default -> throw new IllegalArgumentException("Unknown protocol: " + config.protocol());
            };

        } catch (Exception e) {
            error = e.getMessage();
            logger.debug("Connection test failed for {}:{} ({}): {}", config.host(), config.port(), config.protocol(), error);
        }

        final var duration = System.currentTimeMillis() - startTime;
        return new TestResult(success, duration, LocalDateTime.now(), error);
    }

    private boolean testTcpConnection(String host, int port, int timeoutMs) throws IOException {
        try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        }
    }

    private boolean testUdpConnection(String host, int port, int timeoutMs) throws IOException {
        try (var socket = new DatagramSocket()) {
            socket.setSoTimeout(timeoutMs);
            final var address = InetAddress.getByName(host);

            // Send empty UDP packet
            final var buffer = new byte[0];
            final var packet = new DatagramPacket(buffer, buffer.length, address, port);
            socket.send(packet);

            // For UDP, we consider it successful if no exception is thrown
            return true;
        }
    }

    @Override
    public String getDescription(TestConfig config) {
        return String.format("%s:%d (%s)",
                config.host(), config.port(), config.protocol());
    }

    @Override
    public void validateConfig(TestConfig config) {
        if (config.host() == null || config.host().trim().isEmpty()) {
            throw new IllegalArgumentException("Host is required for connection test");
        }
        if (config.port() <= 0 || config.port() > 65535) {
            throw new IllegalArgumentException("Valid port (1-65535) is required for connection test");
        }
        if (config.protocol() == null) {
            throw new IllegalArgumentException("Protocol must be 'tcp' or 'udp' for connection test");
        }
    }
}
