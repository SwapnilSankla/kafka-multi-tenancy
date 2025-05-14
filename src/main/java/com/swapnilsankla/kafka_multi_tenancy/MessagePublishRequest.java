package com.swapnilsankla.kafka_multi_tenancy;

public record MessagePublishRequest(String tenantId, String message) {}
