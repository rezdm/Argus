package net.argus.tests;

import net.argus.TestConfig;
import net.argus.TestResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.time.LocalDateTime;

public class NetworkTestUrl implements NetworkTest {
    private static final Logger logger = LoggerFactory.getLogger(NetworkTestUrl.class);

    @Override
    public TestResult execute(TestConfig config, int timeoutMs) {
        final var startTime = System.currentTimeMillis();
        var success = false;
        String error = null;

        try {
            validateConfig(config);
            success = performHttpRequest(config.url(), config.proxy(), timeoutMs);
        } catch (Exception e) {
            error = e.getMessage();
            logger.debug("URL test failed for {}: {}", config.url(), error);
        }

        final var duration = System.currentTimeMillis() - startTime;
        return new TestResult(success, duration, LocalDateTime.now(), error);
    }

    private boolean performHttpRequest(String urlString, String proxyUrl, int timeoutMs) throws Exception {
        final var url = new URL(urlString);
        HttpURLConnection connection = null;

        try {
            // Set up proxy if provided
            if (proxyUrl != null && !proxyUrl.trim().isEmpty()) {
                final var proxy = new URL(proxyUrl);
                final var proxyHost = proxy.getHost();
                final var proxyPort = proxy.getPort() != -1 ? proxy.getPort() :
                        (proxy.getProtocol().equals("https") ? 443 : 80);

                final var httpProxy = new Proxy(Proxy.Type.HTTP,
                        new InetSocketAddress(proxyHost, proxyPort));
                connection = (HttpURLConnection) url.openConnection(httpProxy);
            } else {
                connection = (HttpURLConnection) url.openConnection();
            }

            // Configure connection
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setInstanceFollowRedirects(true);
            connection.setUseCaches(false);

            // Set reasonable User-Agent
            connection.setRequestProperty("User-Agent",
                    "Argus-Monitor/1.0 (Network Monitor)");
            connection.setRequestProperty("Accept", "*/*");
            connection.setRequestProperty("Connection", "close");

            // Connect and get response
            connection.connect();
            final var responseCode = connection.getResponseCode();

            // Consider success: 2xx status codes
            final var success = responseCode >= 200 && responseCode < 300;

            if (!success) {
                logger.debug("URL test failed for {}: HTTP {} {}",
                        urlString, responseCode, connection.getResponseMessage());
            }

            return success;

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @Override
    public String getDescription(TestConfig config) {
        return String.format("URL: %s%s",
                config.url(),
                config.proxy() != null && !config.proxy().trim().isEmpty() ? " (via proxy)" : "");
    }

    @Override
    public void validateConfig(TestConfig config) {
        if (config.url() == null || config.url().trim().isEmpty()) {
            throw new IllegalArgumentException("URL is required for URL test");
        }

        try {
            new URL(config.url());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URL format: " + config.url());
        }

        // Validate proxy URL if provided
        if (config.proxy() != null && !config.proxy().trim().isEmpty()) {
            try {
                new URL(config.proxy());
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid proxy URL format: " + config.proxy());
            }
        }
    }
}
