package co.edu.elecciones.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "assistant_message")
public class AssistantMessage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    public AssistantSession session;

    @Column(nullable = false, length = 20)
    public String role;

    @Column(nullable = false, columnDefinition = "TEXT")
    public String content;

    @Column(length = 30)
    public String provider;

    @Column(length = 100)
    public String model;

    @Column(length = 80)
    public String intent;

    @Column(name = "tools_used", length = 1000)
    public String toolsUsed;

    @Column(nullable = false)
    public Boolean fallback = false;

    @Column(name = "response_time_ms")
    public Long responseTimeMs;

    public Boolean helpful;

    @Column(name = "feedback_comment", length = 500)
    public String feedbackComment;
}
