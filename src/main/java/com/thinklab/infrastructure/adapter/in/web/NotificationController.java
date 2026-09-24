package com.thinklab.infrastructure.adapter.in.web;

import com.thinklab.application.dto.request.FailNotificationRequest;
import com.thinklab.application.dto.request.InitiateNotificationRequest;
import com.thinklab.application.dto.response.NotificationResponse;
import com.thinklab.application.usecase.ControlNotificationUseCase;
import com.thinklab.application.usecase.FailNotificationUseCase;
import com.thinklab.application.usecase.InitiateNotificationUseCase;
import com.thinklab.application.usecase.RetrieveNotificationUseCase;
import com.thinklab.application.usecase.RetrieveNotificationsUseCase;
import com.thinklab.domain.model.Notification.NotificationStatus;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Inbound Web Adapter for the {@code notification-dispatch} Service Domain.
 *
 * <p><b>BIAN-Aligned Resource Model (ADR-013):</b> {@link com.thinklab.domain.model.Notification} is
 * the Control Record. Every route follows
 * {@code /notification-dispatch/v1/{control-record-id}/{behavior-qualifier}}. There is no
 * {@code DELETE}: a Notification is either {@code DELIVERED} or {@code CANCELLED}.
 *
 * <p><b>Not the automatic path:</b> these {@code control/*} routes exist for manual ops/Postman/tests.
 * The automatic PENDING -&gt; SENDING -&gt; (DELIVERED|FAILED) flow triggered by an inbound event runs
 * through {@link com.thinklab.application.usecase.DispatchNotificationUseCase} directly, not through
 * this controller (ADR-023).
 */
@Controller("/notification-dispatch/v1")
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);
    static final String TENANT_HEADER = "X-Tenant-Id";
    static final String EXECUTOR_HEADER = "X-Executor";

    private final InitiateNotificationUseCase initiateNotificationUseCase;
    private final RetrieveNotificationUseCase retrieveNotificationUseCase;
    private final RetrieveNotificationsUseCase retrieveNotificationsUseCase;
    private final ControlNotificationUseCase controlNotificationUseCase;
    private final FailNotificationUseCase failNotificationUseCase;

    public NotificationController(
            InitiateNotificationUseCase initiateNotificationUseCase,
            RetrieveNotificationUseCase retrieveNotificationUseCase,
            RetrieveNotificationsUseCase retrieveNotificationsUseCase,
            ControlNotificationUseCase controlNotificationUseCase,
            FailNotificationUseCase failNotificationUseCase
    ) {
        this.initiateNotificationUseCase = initiateNotificationUseCase;
        this.retrieveNotificationUseCase = retrieveNotificationUseCase;
        this.retrieveNotificationsUseCase = retrieveNotificationsUseCase;
        this.controlNotificationUseCase = controlNotificationUseCase;
        this.failNotificationUseCase = failNotificationUseCase;
    }

    /** Behavior Qualifier: {@code initiate}. Registers a new Notification Control Record (PENDING). */
    @Post("/initiate")
    public Mono<HttpResponse<NotificationResponse>> initiate(
            @Header(TENANT_HEADER) @NotBlank String tenantId,
            @Body @Valid InitiateNotificationRequest request
    ) {
        log.info("[ACTION: INITIATE_NOTIFICATION] Received request for organisation: {} recipient: {}", tenantId, request.recipient());

        return initiateNotificationUseCase.execute(UUID.fromString(tenantId), request)
                .map(HttpResponse::created);
    }

    /** Behavior Qualifier: {@code retrieve}. Fetches a single Notification by UUID. */
    @Get("/{id}/retrieve")
    public Mono<HttpResponse<NotificationResponse>> retrieveById(@PathVariable UUID id) {
        log.info("[ACTION: RETRIEVE_NOTIFICATION] Received request to get notification by ID: {}", id);

        return retrieveNotificationUseCase.execute(id).map(HttpResponse::ok);
    }

    /** Behavior Qualifier: {@code retrieve} (collection). Lists Notifications scoped to a tenant. */
    @Get("/retrieve")
    public Mono<List<NotificationResponse>> retrieveAll(
            @Header(TENANT_HEADER) @NotBlank String tenantId,
            @QueryValue @Nullable NotificationStatus status
    ) {
        log.info("[ACTION: RETRIEVE_NOTIFICATIONS] Received request to list notifications for organisation: {} status: {}", tenantId, status);

        return Mono.defer(() -> retrieveNotificationsUseCase.execute(UUID.fromString(tenantId), status).collectList());
    }

    /** Behavior Qualifier: {@code control/send}. */
    @Put("/{id}/control/send")
    public Mono<HttpResponse<Void>> controlSend(@PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor) {
        return control(id, ControlNotificationUseCase.Action.SEND, executor);
    }

    /** Behavior Qualifier: {@code control/deliver}. */
    @Put("/{id}/control/deliver")
    public Mono<HttpResponse<Void>> controlDeliver(@PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor) {
        return control(id, ControlNotificationUseCase.Action.DELIVER, executor);
    }

    /** Behavior Qualifier: {@code control/fail}. The only control action carrying a body. */
    @Put("/{id}/control/fail")
    public Mono<HttpResponse<Void>> controlFail(
            @PathVariable UUID id,
            @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Body @Valid FailNotificationRequest request
    ) {
        log.info("[ACTION: CONTROL_NOTIFICATION] [EXECUTOR: {}] FAIL for ID: {}", executor, id);

        return failNotificationUseCase.execute(id, executor, request.errorMessage()).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code control/retry}. Allowed from FAILED or SENDING (see the domain model). */
    @Put("/{id}/control/retry")
    public Mono<HttpResponse<Void>> controlRetry(@PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor) {
        return control(id, ControlNotificationUseCase.Action.RETRY, executor);
    }

    /** Behavior Qualifier: {@code control/cancel}. */
    @Put("/{id}/control/cancel")
    public Mono<HttpResponse<Void>> controlCancel(@PathVariable UUID id, @Header(EXECUTOR_HEADER) @NotBlank String executor) {
        return control(id, ControlNotificationUseCase.Action.CANCEL, executor);
    }

    private Mono<HttpResponse<Void>> control(UUID id, ControlNotificationUseCase.Action action, String executor) {
        log.info("[ACTION: CONTROL_NOTIFICATION] [EXECUTOR: {}] {} for ID: {}", executor, action, id);

        return controlNotificationUseCase.execute(id, action, executor).thenReturn(HttpResponse.noContent());
    }
}
