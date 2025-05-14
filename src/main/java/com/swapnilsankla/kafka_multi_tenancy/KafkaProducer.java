package com.swapnilsankla.kafka_multi_tenancy;

import com.swapnilsankla.kafka_multi_tenancy.config.TenantConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.CLIENT_ID_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG;

@Component
public class KafkaProducer {
  private final TenantConfig tenantConfig;
  private final Map<String, KafkaTemplate<String, String>> kafkaTemplates = new ConcurrentHashMap<>();

  public KafkaProducer(TenantConfig tenantConfig) {
    this.tenantConfig = tenantConfig;
  }

  public void publish(final String tenantId, final String message) {
    kafkaTemplates.computeIfAbsent(tenantId, this::createKafkaTemplate).send(getTopicForTenant(tenantId), message);
  }

  private KafkaTemplate<String, String> createKafkaTemplate(final String tenantId) {
    Map<String, Object> producerProps = new HashMap<>();
    producerProps.put(BOOTSTRAP_SERVERS_CONFIG, getBootstrapServerForTenant(tenantId));
    producerProps.put(KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    producerProps.put(VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    producerProps.put(CLIENT_ID_CONFIG, "producer-" + tenantId);

    ProducerFactory<String, String> producerFactory = new DefaultKafkaProducerFactory<>(producerProps);
    return new KafkaTemplate<>(producerFactory);
  }

  private String getBootstrapServerForTenant(final String tenantId) {
    return tenantConfig.groups().values().stream()
      .filter(config -> config.kafka().tenants().contains(tenantId))
      .findFirst()
      .map(config -> config.kafka().bootstrapServer())
      .orElseThrow(
        () -> new IllegalArgumentException("Tenant " + tenantId + " not supported"));
  }

  private String getTopicForTenant(String tenantId) {
    // Logic for mapping tenantId to topic
    return tenantId + "-output-topic";
  }
}
