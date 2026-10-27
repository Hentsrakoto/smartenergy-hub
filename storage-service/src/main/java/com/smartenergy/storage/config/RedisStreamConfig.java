package com.smartenergy.storage.config;

import com.smartenergy.storage.service.RedisStreamConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Configuration
public class RedisStreamConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisStreamConfig.class);

    @Bean
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> streamMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisStreamProperties properties,
            RedisStreamConsumer consumer) {

        initConsumerGroup(connectionFactory, properties);

        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions.<String, MapRecord<String, String, String>>builder()
                        .pollTimeout(Duration.ofMillis(properties.getPollTimeoutMs()))
                        .batchSize(properties.getBatchSize())
                        .build();

        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                StreamMessageListenerContainer.create(connectionFactory, options);

        container.receive(
                Consumer.from(properties.getGroup(), properties.getConsumerName()),
                StreamOffset.create(properties.getName(), ReadOffset.lastConsumed()),
                consumer
        );

        container.start();
        log.info("StreamMessageListenerContainer démarré: flux='{}', groupe='{}', consommateur='{}'",
                properties.getName(), properties.getGroup(), properties.getConsumerName());

        return container;
    }

    private void initConsumerGroup(RedisConnectionFactory connectionFactory, RedisStreamProperties properties) {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            try {
                connection.streamCommands().xGroupCreate(
                        properties.getName().getBytes(StandardCharsets.UTF_8),
                        properties.getGroup(),
                        ReadOffset.from("0"),
                        true // makeStream = true (équivalent XGROUP CREATE ... MKSTREAM)
                );
                log.info("Consumer Group '{}' initialisé avec succès sur le flux '{}' (offset: 0)",
                        properties.getGroup(), properties.getName());
            } catch (RedisSystemException e) {
                if (isBusyGroup(e)) {
                    log.info("Le Consumer Group '{}' existe déjà sur le flux '{}'.",
                            properties.getGroup(), properties.getName());
                } else {
                    log.warn("Notice lors de l'initialisation du Consumer Group: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Erreur lors de l'initialisation du Consumer Group Redis: {}", e.getMessage(), e);
        }
    }

    /**
     * Redis signale un groupe déjà existant via une exception dont le message utile
     * ({@code BUSYGROUP ...}) se trouve généralement dans la cause, pas dans le wrapper
     * Spring {@link RedisSystemException}.
     */
    private boolean isBusyGroup(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("BUSYGROUP")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
