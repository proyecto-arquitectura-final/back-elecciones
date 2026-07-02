package co.edu.elecciones.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "assistant_session")
public class AssistantSession extends BaseEntity {

    @Column(name = "session_key", nullable = false, unique = true, updatable = false)
    public UUID sessionKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "election_id")
    public Election election;

    @Column(nullable = false, length = 20)
    public String status = "ACTIVE";

    @Column(nullable = false, length = 30)
    public String provider = "GEMINI";

    @Column(length = 100)
    public String model;

    @Column(name = "last_activity_at", nullable = false)
    public Instant lastActivityAt = Instant.now();

    @Column(name = "message_count", nullable = false)
    public Integer messageCount = 0;
}
