package com.thinklab.domain.exception;

/**
 * Domain Exception: Indicates an illegal lifecycle transition or a business-rule violation on an
 * {@link com.thinklab.domain.model.Notification} (for example, deploying an notification with no location, or
 * mutating a decommissioned notification).
 *
 * <p>RFC 7807 mapping: HTTP 409 Conflict (AST-03, ADR-019). The request is well formed but collides with
 * the aggregate's current state, the same contract used for every other state conflict on the platform.
 */
public class InvalidNotificationStatusException extends BusinessException {

    private static final String ERROR_CODE = "ERR-NTF-00409";

    public InvalidNotificationStatusException(String message) {
        super(ERROR_CODE, message);
    }
}
