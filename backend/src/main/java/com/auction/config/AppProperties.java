package com.auction.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, List<String> corsAllowedOrigins, Seed seed, Demo demo) {

    public record Jwt(String secret, long expirationMinutes) {}

    public record Seed(boolean enabled) {}

    public record Demo(boolean botsEnabled) {}
}
