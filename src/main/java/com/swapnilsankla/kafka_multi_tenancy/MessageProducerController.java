package com.swapnilsankla.kafka_multi_tenancy;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/message")
public class MessageProducerController {
  private final KafkaProducer kafkaProducer;

  public MessageProducerController(KafkaProducer kafkaProducer) {
    this.kafkaProducer = kafkaProducer;
  }

  @PostMapping("")
  public void publish(@RequestBody final MessagePublishRequest request) {
    kafkaProducer.publish(request.tenantId(), request.message());
  }
}

