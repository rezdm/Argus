package net.argus;

public record TestConfig (
      TestMethod testMethod
    , Protocol protocol
    , int port
    , String url
    , String proxy
    , String host
) {}

