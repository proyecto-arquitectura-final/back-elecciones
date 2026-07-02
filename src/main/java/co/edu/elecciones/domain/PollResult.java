package co.edu.elecciones.domain; import jakarta.persistence.*; import com.fasterxml.jackson.annotation.JsonIgnore;
@Entity public class PollResult extends BaseEntity { @ManyToOne @JsonIgnore public Poll poll; @ManyToOne public Candidate candidate; public Double percentage; }
