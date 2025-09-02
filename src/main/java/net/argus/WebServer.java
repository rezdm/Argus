package net.argus;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

class WebServer {
    private static final Logger logger = LoggerFactory.getLogger(WebServer.class);
    private final HttpServer server;
    private final MonitorConfig config;
    private final Map<String, MonitorState> monitors;

    WebServer(MonitorConfig config, Map<String, MonitorState> monitors) throws Exception {
        this.config = config;
        this.monitors = monitors;
        final var parts = config.listen().split(":");
        final var host = parts[0];
        final var port = Integer.parseInt(parts[1]);

        final var address = "localhost".equals(host) ? new InetSocketAddress(port) : new InetSocketAddress(host, port);

        server = HttpServer.create(address, 0);
        server.createContext("/", new StatusHandler());
        server.setExecutor(null);
        server.start();

        logger.info("Argus web server started on {}", config.listen());
    }

    public void stop(){
        if (server != null) {
            server.stop(0);
            logger.info("Web server stopped");
        }
    }

    private class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            final var clientAddress = exchange.getRemoteAddress().toString();
            logger.debug("HTTP request from {}: {} {}",
                    clientAddress, exchange.getRequestMethod(), exchange.getRequestURI());

            final var response = generateStatusPage();

            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.getBytes().length);

            try (var os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }

            logger.trace("Served status page to {} ({} bytes)", clientAddress, response.length());
        }

        private String generateStatusPage() {
            final var html = new StringBuilder();
            html.append("""
            <!DOCTYPE html>
            <html>
            <head>
                <title>%s - Network Monitor</title>
                <meta charset="UTF-8">
                <meta http-equiv="refresh" content="30">
                <style>
                    body { font-family: Arial, sans-serif; margin: 20px; background-color: #f5f5f5; }
                    .header { background-color: #2c3e50; color: white; padding: 20px; border-radius: 5px; margin-bottom: 20px; }
                    .group { background-color: white; margin-bottom: 20px; border-radius: 5px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
                    .group-header { background-color: #34495e; color: white; padding: 15px; border-radius: 5px 5px 0 0; font-size: 18px; font-weight: bold; }
                    .monitor-table { width: 100%%; border-collapse: collapse; }
                    .monitor-table th, .monitor-table td { padding: 12px; text-align: left; border-bottom: 1px solid #ddd; }
                    .monitor-table th { background-color: #ecf0f1; font-weight: bold; }
                    .status-ok { color: #27ae60; font-weight: bold; }
                    .status-warning { color: #f39c12; font-weight: bold; }
                    .status-error { color: #e74c3c; font-weight: bold; }
                    .last-updated { text-align: center; margin-top: 20px; color: #7f8c8d; font-style: italic; }
                    .uptime-bar { width: 100px; height: 20px; background-color: #ecf0f1; border-radius: 10px; overflow: hidden; position: relative; }
                    .uptime-fill { height: 100%%; background-color: #27ae60; transition: width 0.3s ease; }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1>%s</h1>
                    <p>Network Monitoring Dashboard</p>
                </div>
            """.formatted(config.name(), config.name()));

            // Group monitors by group name
            Map<String, List<MonitorState>> groupedMonitors = new TreeMap<>();
            for (var state : monitors.values()) {
                groupedMonitors.computeIfAbsent(state.groupName, k -> new ArrayList<>()).add(state);
            }

            for (var groupEntry : groupedMonitors.entrySet()) {
                html.append("""
                <div class="group">
                    <div class="group-header">%s</div>
                    <table class="monitor-table">
                        <thead>
                            <tr>
                                <th>Service</th>
                                <th>Host</th>
                                <th>Status</th>
                                <th>Response Time</th>
                                <th>Uptime</th>
                                <th>Last Check</th>
                                <th>Details</th>
                            </tr>
                        </thead>
                        <tbody>
                """.formatted(groupEntry.getKey()));

                // Sort by destination sort order
                final var sortedStates = groupEntry.getValue();
                sortedStates.sort(Comparator.comparing(s -> s.destination.sort()));

                for (var state : sortedStates) {
                    final var statusClass = switch (state.getCurrentStatus()) {
                        case OK -> "status-ok";
                        case WARNING -> "status-warning";
                        case FAILURE -> "status-error";
                    };

                    final var statusText = switch (state.getCurrentStatus()) {
                        case OK -> "OK";
                        case WARNING -> "WARNING";
                        case FAILURE -> "FAILURE";
                    };

                    final var lastResult = state.getLastResult();
                    final var lastCheck = lastResult != null ?
                            lastResult.timestamp().format(DateTimeFormatter.ofPattern("HH:mm:ss")) : "Never";

                    final var responseTime = lastResult != null ? lastResult.duration() + "ms" : "N/A";
                    final var uptimePercent = state.getUptimePercentage();

                    final var testDetails = state.getTestDescription();

                    html.append("""
                            <tr>
                                <td>%s</td>
                                <td>%s</td>
                                <td class="%s">%s</td>
                                <td>%s</td>
                                <td>
                                    <div class="uptime-bar">
                                        <div class="uptime-fill" style="width: %.1f%%"></div>
                                    </div>
                                    %.1f%%
                                </td>
                                <td>%s</td>
                                <td>%s</td>
                            </tr>
                    """.formatted(
                            state.destination.name(),
                            state.destination.test().host(),
                            statusClass,
                            statusText,
                            responseTime,
                            uptimePercent,
                            uptimePercent,
                            lastCheck,
                            testDetails
                    ));
                }

                html.append("""
                        </tbody>
                    </table>
                </div>
                """);
            }

            html.append("""
                <div class="last-updated">
                    Last updated: %s | Auto-refresh every 30 seconds
                </div>
            </body>
            </html>
            """.formatted(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));

            return html.toString();
        }
    }
}
