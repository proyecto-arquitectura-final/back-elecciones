package co.edu.elecciones.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "report_generation")
public class ReportGeneration extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "election_id", nullable = false)
    public Election election;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    public ReportFormat format;

    @Column(name = "requested_by", nullable = false, length = 255)
    public String requestedBy;

    @Column(name = "record_count", nullable = false)
    public Long recordCount = 0L;

    @Column(name = "generated_at", nullable = false)
    public Instant generatedAt = Instant.now();
}
