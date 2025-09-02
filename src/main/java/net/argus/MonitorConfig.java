package net.argus;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

record MonitorConfig (String name, String listen, List<Group> monitors){
    static MonitorConfig loadConfig(String configPath) throws Exception { //parse it manually, skip library import, etc
        final var mapper = new ObjectMapper();
        final var configContent = Files.readString(Paths.get(configPath));

        // Simple JSON parsing for the configuration
        final var root = mapper.readTree(configContent);

        final var config = new MonitorConfig(root.get("name").asText(), root.get("listen").asText(), new ArrayList<>());

        final var monitorsNode = root.get("monitors");
        for (var monitorNode : monitorsNode) {
            final var group = new Group(monitorNode.get("sort").asInt(), monitorNode.get("group").asText(), new ArrayList<>());

            final var destinationsNode = monitorNode.get("destinations");
            for (var destNode : destinationsNode) {
                final var testNode = destNode.get("test");
                final var method = testNode.get("method").asText();
                final var protocol = testNode.has("protocol") ? testNode.get("protocol").asText() : null;
                final var port = testNode.has("port") ? testNode.get("port").asInt() : -1;
                final var url = testNode.has("url") ? testNode.get("url").asText() : null;
                final var proxy = testNode.has("proxy") ? testNode.get("proxy").asText() : null;
                final var host = testNode.has("host") ? testNode.get("host").asText() : null;

                final var dest = new Destination(
                        destNode.get("sort").asInt()
                        , destNode.get("name").asText()
                        , destNode.get("timeout").asInt()
                        , destNode.get("warning").asInt()
                        , destNode.get("failure").asInt()
                        , destNode.get("reset").asInt()
                        , destNode.get("interval").asInt()
                        , destNode.get("history").asInt()
                        , new TestConfig(TestMethod.valueOf(method), protocol == null ? null : Protocol.valueOf(protocol), port, url, proxy, host)
                );
                group.destinations().add(dest);
            }
            group.destinations().sort(Comparator.comparing(Destination::sort));
            config.monitors().add(group);
        }
        config.monitors().sort(Comparator.comparing(Group::sort));

        return config;
    }
}

record Group (int sort, String group, List<Destination> destinations) {}

record Destination(int sort, String name, int timeout, int warning, int failure, int reset, int interval, int history, TestConfig test) {
    public Destination {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Destination name cannot be empty");
        }
        if (timeout <= 0) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
        if (warning <= 0 || failure <= 0) {
            throw new IllegalArgumentException("Warning and failure thresholds must be positive");
        }
    }}
