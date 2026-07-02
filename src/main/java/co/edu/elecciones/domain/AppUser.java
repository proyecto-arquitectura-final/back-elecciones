package co.edu.elecciones.domain; import jakarta.persistence.*; import jakarta.validation.constraints.*;
@Entity public class AppUser extends BaseEntity { @NotBlank public String name; @Email @Column(unique=true,nullable=false) public String email; @Column(nullable=false) public String password; @Enumerated(EnumType.STRING) public Role role; public boolean active=true; }
