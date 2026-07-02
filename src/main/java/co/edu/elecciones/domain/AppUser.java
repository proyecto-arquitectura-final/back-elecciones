package co.edu.elecciones.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;

@Entity
public class AppUser extends BaseEntity {
    @NotBlank
    public String name;
    @Email
    @Column(unique = true, nullable = false)
    public String email;
    @JsonIgnore
    @Column(nullable = false)
    public String password;
    @Enumerated(EnumType.STRING)
    public Role role;
    public boolean active = true;
}
