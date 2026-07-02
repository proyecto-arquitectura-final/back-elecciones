package co.edu.elecciones.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "election_result_summary")
public class ElectionResultSummary extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "election_id", nullable = false, unique = true)
    public Election election;

    @Column(name = "eligible_voters", nullable = false)
    public Long eligibleVoters = 0L;

    @Column(name = "total_voters", nullable = false)
    public Long totalVoters = 0L;

    @Column(name = "valid_votes", nullable = false)
    public Long validVotes = 0L;

    @Column(name = "blank_votes", nullable = false)
    public Long blankVotes = 0L;

    @Column(name = "null_votes", nullable = false)
    public Long nullVotes = 0L;

    @Column(name = "unmarked_votes", nullable = false)
    public Long unmarkedVotes = 0L;

    @Column(name = "reported_tables", nullable = false)
    public Integer reportedTables = 0;

    @Column(name = "total_tables", nullable = false)
    public Integer totalTables = 0;

    @Column(nullable = false)
    public String source = "CARGA_MANUAL";

    @Column(name = "imported_at", nullable = false)
    public Instant importedAt = Instant.now();
}
