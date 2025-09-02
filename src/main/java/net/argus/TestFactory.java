package net.argus;

import net.argus.tests.NetworkTest;
import net.argus.tests.NetworkTestConnect;
import net.argus.tests.NetworkTestPing;
import net.argus.tests.NetworkTestUrl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TestFactory {
    private static final Map<TestMethod, NetworkTest> testImplementations = new ConcurrentHashMap<>();

    static {
        testImplementations.put(TestMethod.Ping, new NetworkTestPing());
        testImplementations.put(TestMethod.Connect, new NetworkTestConnect());
        testImplementations.put(TestMethod.Url, new NetworkTestUrl());
    }

    public static NetworkTest getTest(TestMethod testMethod) {
        if (testMethod == null) {
            throw new IllegalArgumentException("Test method cannot be null or empty");
        }

        NetworkTest test = testImplementations.get(testMethod);
        if (test == null) {
            throw new IllegalArgumentException("Unsupported test method: " + testMethod + ". Supported methods: " + testImplementations.keySet());
        }

        return test;
    }

    public static void registerTest(TestMethod method, NetworkTest implementation) {
        if (method == null || implementation == null) {
            throw new IllegalArgumentException("Method and implementation cannot be null");
        }
        testImplementations.put(method, implementation);
    }

    public static java.util.Set<TestMethod> getSupportedMethods() {
        return testImplementations.keySet();
    }

    public static String validateAndDescribe(TestConfig config) {
        NetworkTest test = getTest(config.testMethod());
        test.validateConfig(config);
        return test.getDescription(config);
    }
}