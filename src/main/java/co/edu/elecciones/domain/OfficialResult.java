package co.edu.elecciones.domain;

import jakarta.persistence.Entity;
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

    public String department;
    public String municipality;
    public Long votes = 0L;
    public Double percentage = 0.0;
    public Integer reportedTables = 0;
    public Integer totalTables = 0;
    public Double participation = 0.0;
    public String source = "CARGA_MANUAL";
    public Instant importedAt = Instant.now();
}
