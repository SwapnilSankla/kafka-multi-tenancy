package com.swapnilsankla.kafka_multi_tenancy.config;

import com.swapnilsankla.kafka_multi_tenancy.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListenerAnnotationBeanPostProcessor;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.apache.kafka.clients.consumer.ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG;

@Configuration
public class BeanConfig {
  private final KafkaListenerAnnotationBeanPostProcessor kafkaProcessor;

  public BeanConfig(final KafkaListenerAnnotationBeanPostProcessor kafkaProcessor) {
    this.kafkaProcessor = kafkaProcessor;
  }

  @Bean
  public List<KafkaConsumer> kafkaConsumers(@Autowired final TenantConfig tenantConfig,
                                            @Autowired final ConfigurableListableBeanFactory beanFactory) {
    return tenantConfig
      .groups()
      .values()
      .stream()
      .flatMap(group -> buildKafkaConsumers(beanFactory, group.kafka()))
      .toList();
  }

  private Stream<KafkaConsumer> buildKafkaConsumers(final ConfigurableListableBeanFactory beanFactory,
                                                    final TenantConfig.GroupConfig.KafkaConfig kafkaConfig) {
    return kafkaConfig
      .tenants()
      .stream()
      .map(tenantId -> {
      var containerFactory = containerFactory(kafkaConfig.bootstrapServer());
      String topicName = buildTopicName(tenantId);
      String groupId = "group-" + tenantId;
      KafkaConsumer kafkaConsumer = new KafkaConsumer(topicName, groupId, containerFactory);
      beanFactory.initializeBean(kafkaConsumer, "KafkaConsumer" + topicName);
      kafkaProcessor.postProcessAfterInitialization(kafkaConsumer, "KafkaConsumer" + topicName);
      return kafkaConsumer;
    });
  }

  private ConsumerFactory<String, String> consumerFactory(final String bootstrapServer) {
    Map<String, Object> props = new HashMap<>();
    props.put(BOOTSTRAP_SERVERS_CONFIG, bootstrapServer);
    props.put(KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    return new DefaultKafkaConsumerFactory<>(props);
  }

  private ConcurrentKafkaListenerContainerFactory<String, String> containerFactory(final String bootstrapServer) {
    ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory(bootstrapServer));
    return factory;
  }

  private String buildTopicName(final String tenant) {
    // logic to derive topic name from tenant
    return tenant;
  }
}
