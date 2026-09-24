package com.thinklab.infrastructure.adapter.out.channel;

import io.micronaut.context.annotation.ConfigurationProperties;

/** Binds {@code thinklab.notifications.webhook.*}. Off by default — no channel is registered until enabled. */
@ConfigurationProperties("thinklab.notifications.webhook")
public class WebhookProperties {

    private boolean enabled;
    private String url;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** The single, globally-configured target URL every WEBHOOK notification posts to. */
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
