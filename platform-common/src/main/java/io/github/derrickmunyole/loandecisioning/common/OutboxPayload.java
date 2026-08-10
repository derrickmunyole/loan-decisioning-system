package io.github.derrickmunyole.loandecisioning.common;

import java.util.UUID;

/** Contract every published domain event implements, in place of a stringly-typed event name. */
public interface OutboxPayload {

    String eventType();

    String aggregateType();

    UUID aggregateId();
}