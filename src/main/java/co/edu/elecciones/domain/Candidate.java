package co.edu.elecciones.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotBlank;

@Entity
public class Candidate extends BaseEntity {
    @NotBlank
    public String name;
    public String vicePresidentName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "party_id", nullable = false)
    public Party party;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "election_id", nullable = false)
    @JsonIgnore
    public Election election;

    @Enumerated(EnumType.STRING)
    public ElectionType electionType;
    public String department;
    public String municipality;
    public boolean active = true;
}
