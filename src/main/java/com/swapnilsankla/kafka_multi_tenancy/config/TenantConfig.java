package com.swapnilsankla.kafka_multi_tenancy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

@ConfigurationProperties("tenant-config")
public record TenantConfig(Map<String, GroupConfig> groups) {
  public record GroupConfig(KafkaConfig kafka) {
    public record KafkaConfig(List<String> tenants, String bootstrapServer) {}
  }
}
