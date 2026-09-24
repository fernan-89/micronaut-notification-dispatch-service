package com.thinklab.infrastructure.adapter.in.event;

import com.thinklab.kit.events.EventsProperties;
import io.micronaut.context.event.StartupEvent;
import io.nats.client.Connection;
import io.nats.client.ConsumerContext;
import io.nats.client.Message;
import io.nats.client.MessageConsumer;
import io.nats.client.MessageHandler;
import io.nats.client.StreamContext;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.impl.NatsJetStreamMetaData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JetStreamNotificationSubscriberTest {

    @Mock private Connection connection;
    @Mock private StreamContext streamContext;
    @Mock private ConsumerContext consumerContext;
    @Mock private MessageConsumer messageConsumer;
    @Mock private UserInitiatedEventHandler eventHandler;
    @Mock private StartupEvent startupEvent;

    private final EventsProperties properties = new EventsProperties();

    @BeforeEach
    void setUp() {
        properties.setStreamName("THINKLAB_EVENTS");
        properties.setSubjectPrefix("thinklab");
    }

    @Test
    @DisplayName("startup subscribes a durable consumer to the user.initiated subject")
    void subscribesOnStartup() throws Exception {
        when(connection.getStreamContext("THINKLAB_EVENTS")).thenReturn(streamContext);
        when(streamContext.createOrUpdateConsumer(any(ConsumerConfiguration.class))).thenReturn(consumerContext);
        when(consumerContext.consume(any(MessageHandler.class))).thenReturn(messageConsumer);

        new JetStreamNotificationSubscriber(connection, properties, eventHandler).onApplicationEvent(startupEvent);

        verify(consumerContext).consume(any(MessageHandler.class));
    }

    @Test
    @DisplayName("a connectivity failure at startup is contained, never propagated")
    void connectivityFailureIsContained() throws Exception {
        when(connection.getStreamContext("THINKLAB_EVENTS")).thenThrow(new java.io.IOException("broker down"));

        new JetStreamNotificationSubscriber(connection, properties, eventHandler).onApplicationEvent(startupEvent);
    }

    @Test
    @DisplayName("a successfully handled message is acked")
    void messageHandledSuccessfully() throws Exception {
        MessageHandler handler = subscribeAndCaptureHandler();
        Message message = mock(Message.class);
        when(message.getData()).thenReturn("payload".getBytes());
        when(eventHandler.handle("payload")).thenReturn(reactor.core.publisher.Mono.empty());

        handler.onMessage(message);

        verify(message).ack();
        verify(message, never()).nak();
        verify(message, never()).term();
    }

    @Test
    @DisplayName("a failed message below MaxDeliver is nak'd for redelivery")
    void messageFailureBelowMaxDeliverIsNakd() throws Exception {
        MessageHandler handler = subscribeAndCaptureHandler();
        Message message = mock(Message.class);
        NatsJetStreamMetaData metaData = mock(NatsJetStreamMetaData.class);
        when(message.getData()).thenReturn("payload".getBytes());
        when(message.metaData()).thenReturn(metaData);
        when(metaData.deliveredCount()).thenReturn(2L);
        when(eventHandler.handle("payload")).thenReturn(reactor.core.publisher.Mono.error(new IllegalStateException("boom")));

        handler.onMessage(message);

        verify(message).nak();
        verify(message, never()).ack();
        verify(message, never()).term();
    }

    @Test
    @DisplayName("a failed message at MaxDeliver is terminated, never redelivered")
    void messageFailureAtMaxDeliverIsTerminated() throws Exception {
        MessageHandler handler = subscribeAndCaptureHandler();
        Message message = mock(Message.class);
        NatsJetStreamMetaData metaData = mock(NatsJetStreamMetaData.class);
        when(message.getData()).thenReturn("payload".getBytes());
        when(message.getSubject()).thenReturn("thinklab.party-authentication.user.initiated");
        when(message.metaData()).thenReturn(metaData);
        when(metaData.deliveredCount()).thenReturn(5L);
        when(eventHandler.handle("payload")).thenReturn(reactor.core.publisher.Mono.error(new IllegalStateException("boom")));

        handler.onMessage(message);

        verify(message).term();
        verify(message, never()).ack();
        verify(message, never()).nak();
    }

    @Test
    @DisplayName("shutdown stops the consumer only if one was created")
    void shutdownStopsConsumerWhenPresent() throws Exception {
        JetStreamNotificationSubscriber subscriber = new JetStreamNotificationSubscriber(connection, properties, eventHandler);
        subscriber.shutdown();

        when(connection.getStreamContext("THINKLAB_EVENTS")).thenReturn(streamContext);
        when(streamContext.createOrUpdateConsumer(any(ConsumerConfiguration.class))).thenReturn(consumerContext);
        when(consumerContext.consume(any(MessageHandler.class))).thenReturn(messageConsumer);
        subscriber.onApplicationEvent(startupEvent);

        subscriber.shutdown();

        verify(messageConsumer).stop();
    }

    @Test
    @DisplayName("mandatory collaborators and the startup event are null-checked")
    void nullGuards() {
        assertThrows(NullPointerException.class, () -> new JetStreamNotificationSubscriber(null, properties, eventHandler));
        assertThrows(NullPointerException.class, () -> new JetStreamNotificationSubscriber(connection, null, eventHandler));
        assertThrows(NullPointerException.class, () -> new JetStreamNotificationSubscriber(connection, properties, null));
        assertThrows(NullPointerException.class,
                () -> new JetStreamNotificationSubscriber(connection, properties, eventHandler).onApplicationEvent(null));
    }

    private MessageHandler subscribeAndCaptureHandler() throws Exception {
        when(connection.getStreamContext("THINKLAB_EVENTS")).thenReturn(streamContext);
        when(streamContext.createOrUpdateConsumer(any(ConsumerConfiguration.class))).thenReturn(consumerContext);
        ArgumentCaptor<MessageHandler> captor = ArgumentCaptor.forClass(MessageHandler.class);
        when(consumerContext.consume(captor.capture())).thenReturn(messageConsumer);

        new JetStreamNotificationSubscriber(connection, properties, eventHandler).onApplicationEvent(startupEvent);

        return captor.getValue();
    }
}
