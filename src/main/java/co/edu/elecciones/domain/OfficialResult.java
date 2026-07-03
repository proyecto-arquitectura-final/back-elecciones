package co.edu.elecciones.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.time.Instant;

@Entity
public class OfficialResult extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "election_id", nullable = false)
    public Election election;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false)
    public Candidate candidate;

    @Column(length = 120)
    public String department;

    @Column(length = 120)
    public String municipality;

    @Column(nullable = false)
    public Long votes = 0L;

    @Column(nullable = false)
    public Double percentage = 0.0;

    @Column(name = "reported_tables", nullable = false)
    public Integer reportedTables = 0;

    @Column(name = "total_tables", nullable = false)
    public Integer totalTables = 0;

    @Column(nullable = false)
    public Double participation = 0.0;

    @Column(nullable = false, length = 160)
    public String source = "CARGA_MANUAL";

    @Column(name = "imported_at", nullable = false)
    public Instant importedAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", nullable = false, length = 20)
    public ResultValidationStatus validationStatus = ResultValidationStatus.PENDIENTE;

    @Column(name = "validation_message", length = 500)
    public String validationMessage;

    @Column(name = "validated_at")
    public Instant validatedAt;

    @Column(name = "validated_by", length = 255)
    public String validatedBy;
}
