package net.argus.tests;

import net.argus.TestConfig;
import net.argus.TestResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.time.LocalDateTime;

public interface NetworkTest {
    TestResult execute(TestConfig config, int timeoutMs);
    String getDescription(TestConfig config);
    void validateConfig(TestConfig config) throws IllegalArgumentException;
}

