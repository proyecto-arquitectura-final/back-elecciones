package co.edu.elecciones.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.time.LocalDate;

@Entity
public class Poll extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "election_id", nullable = false)
    public Election election;

    @Column(nullable = false, length = 160)
    public String source;

    @Column(nullable = false)
    public LocalDate date;

    @Column(nullable = false)
    public Integer sampleSize;

    @Column(nullable = false)
    public Double marginError;

    @Column(nullable = false, length = 500)
    public String methodology;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public PollStatus status = PollStatus.PENDIENTE;
}
