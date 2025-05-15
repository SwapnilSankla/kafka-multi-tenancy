package com.swapnilsankla.kafka_multi_tenancy;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;

import static org.springframework.kafka.support.KafkaHeaders.RECEIVED_TOPIC;

public record KafkaConsumer(String topic, String groupId,
                            ConcurrentKafkaListenerContainerFactory<String, String> factory) {
  @RetryableTopic(
    backoff = @Backoff(delay = 2000),
    dltTopicSuffix = ".dlt")
  @KafkaListener(topics = "#{__listener.topic}", groupId = "#{__listener.groupId}", containerFactory = "#{__listener.factory}")
  public void listen(String message, @Header(RECEIVED_TOPIC) String topic) {
    System.out.println("Received Message on topic: " + topic + ", message: " + message);
  }
}
