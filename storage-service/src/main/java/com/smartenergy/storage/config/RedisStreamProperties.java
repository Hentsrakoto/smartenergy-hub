package com.smartenergy.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

@Configuration
@ConfigurationProperties(prefix = "smartenergy.stream")
public class RedisStreamProperties {

    private String name = "energy-measurements";
    private String group = "storage-group";
    private String consumerPrefix = "storage-consumer";
    private long pollTimeoutMs = 1000;
    private int batchSize = 10;
    private long recoveryIntervalMs = 10000;

    // Identifiant unique pour ce processus consommateur (ex: storage-consumer-a1b2c3d4)
    private final String consumerName = "storage-consumer-" + UUID.randomUUID().toString().substring(0, 8);

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getConsumerPrefix() {
        return consumerPrefix;
    }

    public void setConsumerPrefix(String consumerPrefix) {
        this.consumerPrefix = consumerPrefix;
    }

    public long getPollTimeoutMs() {
        return pollTimeoutMs;
    }

    public void setPollTimeoutMs(long pollTimeoutMs) {
        this.pollTimeoutMs = pollTimeoutMs;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public long getRecoveryIntervalMs() {
        return recoveryIntervalMs;
    }

    public void setRecoveryIntervalMs(long recoveryIntervalMs) {
        this.recoveryIntervalMs = recoveryIntervalMs;
    }

    public String getConsumerName() {
        return consumerName;
    }
}
