package co.edu.elecciones.domain; import jakarta.persistence.*; import jakarta.validation.constraints.*;
@Entity public class Party extends BaseEntity { @NotBlank @Column(unique=true) public String name; @NotBlank @Column(unique=true) public String acronym; public String color; public Integer foundationYear; public boolean active=true; }
