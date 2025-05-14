# Kafka Multi Tenancy

## Overview
Imagine you're building a SaaS application that relies heavily on Apache Kafka to process high volumes of streaming data. As your customer base grows and data throughput increases, a single Kafka cluster might not be sufficient to handle the load. To scale effectively, you might choose to split your Kafka infrastructure into multiple clusters or groups.

Each Kafka cluster (or group) will have its own bootstrap servers, and managing this complexity at the application level becomes essential.

## The Challenge
When your system spans multiple Kafka clusters, you’re faced with a key architectural decision:

```How do you manage Kafka connectivity across clusters while maintaining tenant isolation and scalability?```

One straightforward approach is to deploy separate instances of your application per tenant, each configured to connect to a different Kafka cluster. While this works, it quickly becomes operationally expensive and complex to manage.

### Why Per-Tenant Application Instances Don't Scale Well
At first glance, deploying a separate instance of your application per tenant might seem like a simple and clean solution. Each instance can be configured with a dedicated Kafka bootstrap server, tenant-specific settings, and isolated compute resources.

However, this approach doesn’t scale well, especially in high-growth SaaS environments. Here’s why:

🔁 **Operational Overhead**:

Managing dozens—or even hundreds—of application instances introduces significant DevOps complexity:
  - You’ll need to maintain separate deployments, configurations, and monitoring for each tenant.
  - CI/CD pipelines become more complex as you must parameterize builds for each tenant.
  - Troubleshooting or rolling out fixes means repeating the process N times—once for every instance.

💸 **Resource Inefficiency**: 

Each application instance consumes memory, CPU, and possibly a dedicated container or VM:
- This leads to under utilization of system resources, especially for low-traffic tenants.
- Scaling infrastructure per tenant can lead to increased cloud costs.

⚙️ **Limited Flexibility**:

- Moving a tenant to a different Kafka cluster requires redeployment of the tenant’s app instance with new configuration.
- Making global changes (e.g., security patches, feature updates) requires coordinating updates across all instances.

🚧 **Increased Risk of Drift**:

- With many per-tenant deployments, it's easy for configurations to drift over time.
- This can lead to inconsistent behavior across tenants and make debugging more difficult.

### The Solution: Application-Level Multi-Tenancy
Instead of deploying a separate instance per tenant, you can implement multi-tenancy directly at the application level. This approach allows a single application instance to dynamically connect to different Kafka clusters based on tenant configuration.

#### Benefits of this design:

✅ **Tenant Isolation**: Tenants can be grouped based on data volume or criticality, with each group assigned to a different Kafka cluster.

🔄 **Flexibility**: You can easily migrate tenants between clusters without redeploying the application.

⚙️ **Operational Efficiency**: Reduces the overhead of managing and deploying multiple instances per tenant.

### What This Repository Contains
This repository provides a reference implementation for building a multi-tenant Kafka consumer using Spring Boot and Java. It demonstrates how to:

- Dynamically route tenant traffic to different Kafka clusters.
- Configure and maintain separate consumer factories per tenant.
- Abstract tenant-specific logic using a registry and service layer.
- The codebase is modular and extensible, making it a solid foundation for building production-grade, multi-tenant Kafka consumers.

## Approach

### Tenant Configuration
The `application.yml` file contains the configuration for the Kafka multi-tenant application. It includes a list of Kafka clusters, each with their own bootstrap servers and tenants.

The idea is to group the tenants into a single cluster based on the load, compliance with security requirements, and other factors.
```yml
tenant-config:
  groups:
    group1:
      kafka:
        bootstrap-server: localhost:9092
        tenants:
          - test-tenant1
    group2:
      kafka:
        bootstrap-server: localhost:9092
        tenants:
          - test-tenant2
```

### 🧩 Dynamic Consumer Creation with Spring Kafka
To support multiple tenants, we create dedicated Kafka consumers based on tenant-specific configuration (tenant-config). A key design goal was to leverage Spring Kafka’s annotation-based support (@KafkaListener) as much as possible rather than falling back to a fully programmatic listener setup.

Why avoid the programmatic approach?
- It introduces unnecessary complexity.
- Features like Kafka retries and error handling must be manually wired.
- You lose the expressive simplicity and auto-configuration benefits provided by Spring.

To retain the power of annotations while allowing dynamic configuration, we use Spring's `ConfigurableListableBeanFactory` to manually register Kafka consumer beans at runtime. This allows us to still take full advantage of the `@KafkaListener` annotation.

But there's a challenge: how do we pass tenant-specific values like topic, group ID, and container factory to the `@KafkaListener` at runtime?

We solve this using `Spring Expression Language (SpEL)` inside the annotation. Here's what the consumer looks like:
```java
public record KafkaConsumer(String topic, String groupId, ConcurrentKafkaListenerContainerFactory<String, String> factory) {
  @KafkaListener(topics = "#{__listener.topic}", groupId = "#{__listener.groupId}", containerFactory = "#{__listener.factory}")
  public void listen(String message, @Header(RECEIVED_TOPIC) String topic) {
    //implementation
  }
}
```

🔑 **Key Advantages**:
- ✅ Maintains the simplicity of @KafkaListener
- 🔧 Dynamically wires in tenant-specific config
- 📦 Keeps the codebase clean and declarative

This design gives us the best of both worlds: the flexibility of dynamic configuration and the ease of use that comes with Spring's annotation-driven Kafka support.

### 🚀 Dynamic Producer Creation with Spring Kafka
At the heart of the producer logic is the `KafkaProducer` class, which maintains an in-memory cache (a map) of `KafkaTemplate` instances—one per tenant. Each `KafkaTemplate` is dynamically created at runtime, using the tenant’s specific Kafka configuration.

Once initialized, the `KafkaTemplate` is reused for the lifetime of the application, ensuring **efficient** resource usage and avoiding redundant object creation.

🔍 **How It Works**

1. When a message needs to be sent, the producer:
   - Looks up the tenant-specific KafkaTemplate from the cache.
   - If not found, it creates one on the fly using the tenant's bootstrap server.
2. It then determines the Kafka topic associated with the tenant.
3. Finally, the message is sent using the appropriate KafkaTemplate.

This pattern decouples tenant routing logic from message production and enables full isolation across tenants, even at the producer level.

🧪 **Example Code Snippet**
```java
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
```

✅ **Key Benefits**
- 🔄 **Dynamic Routing**: Send messages to different Kafka clusters based on the tenant context.
- ⚡ **Cached Templates**: No repeated instantiation—KafkaTemplate is created once per tenant.
- 🧩 **Clean Abstraction**: Business logic remains unaware of Kafka's internal details.

This setup completes the multi-tenancy story by enabling both consuming and producing messages per tenant, with dynamic Kafka configuration, all within a single Spring Boot application.