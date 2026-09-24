package com.thinklab.infrastructure.adapter.out.channel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WebhookPropertiesTest {

    @Test
    @DisplayName("off by default, every property is settable")
    void settersRoundTrip() {
        WebhookProperties properties = new WebhookProperties();

        assertFalse(properties.isEnabled());

        properties.setEnabled(true);
        properties.setUrl("http://localhost:9999/webhook");

        assertEquals(true, properties.isEnabled());
        assertEquals("http://localhost:9999/webhook", properties.getUrl());
    }
}
