package co.edu.elecciones.domain;

import jakarta.persistence.Entity;

import java.time.LocalDate;

@Entity
public class Poll extends BaseEntity {
    public String source;
    public LocalDate date;
    public Integer sampleSize;
    public Double marginError;
    public String methodology;
}
