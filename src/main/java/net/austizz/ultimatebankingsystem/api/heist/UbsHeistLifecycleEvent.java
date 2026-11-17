package net.austizz.ultimatebankingsystem.api.heist;

import java.util.Set;
import java.util.UUID;

/** Notification of a heist lifecycle transition. */
public final class UbsHeistLifecycleEvent {
    public enum Stage { STARTED, ALARMED, SUCCEEDED, FAILED }

    private final Stage stage;
    private final UUID sessionId;
    private final UUID bankId;
    private final String premiseId;
    private final Set<UUID> crew;
    private final String detail;

    public UbsHeistLifecycleEvent(Stage stage, UUID sessionId, UUID bankId, String premiseId,
                                  Set<UUID> crew, String detail) {
        this.stage = stage;
        this.sessionId = sessionId;
        this.bankId = bankId;
        this.premiseId = premiseId == null ? "" : premiseId;
        this.crew = crew == null ? Set.of() : Set.copyOf(crew);
        this.detail = detail == null ? "" : detail;
    }

    public Stage stage() { return stage; }
    public UUID sessionId() { return sessionId; }
    public UUID bankId() { return bankId; }
    public String premiseId() { return premiseId; }
    public Set<UUID> crew() { return crew; }
    public String detail() { return detail; }
}
