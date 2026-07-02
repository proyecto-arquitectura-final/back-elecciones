package co.edu.elecciones.controller;

import co.edu.elecciones.commons.dto.ApiResponse;
import co.edu.elecciones.domain.AppUser;
import co.edu.elecciones.dto.Requests.UserRequest;
import co.edu.elecciones.repository.UserRepository;
import co.edu.elecciones.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/usuarios")
public class UsuariosController {
    private final UserRepository repository;
    private final AuditService audit;
    private final PasswordEncoder passwordEncoder;

    public UsuariosController(UserRepository repository, AuditService audit, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.audit = audit;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public ApiResponse<List<AppUser>> all() {
        return ApiResponse.ok("OK", repository.selectAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<AppUser> one(@PathVariable Long id) {
        return ApiResponse.ok("OK", repository.selectById(id).orElseThrow());
    }

    @PostMapping
    public ApiResponse<AppUser> create(@RequestBody UserRequest request, HttpServletRequest http) {
        AppUser user = new AppUser();
        apply(user, request, true);
        AppUser saved = repository.save(user);
        audit.log("CREATE", "AppUser", saved.id, "Creación", http);
        return ApiResponse.ok("Creado", saved);
    }

    @PutMapping("/{id}")
    public ApiResponse<AppUser> update(@PathVariable Long id, @RequestBody UserRequest request,
                                       HttpServletRequest http) {
        AppUser user = repository.selectById(id).orElseThrow();
        apply(user, request, false);
        AppUser saved = repository.save(user);
        audit.log("UPDATE", "AppUser", saved.id, "Actualización", http);
        return ApiResponse.ok("Actualizado", saved);
    }

    @Transactional
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest http) {
        repository.selectById(id).orElseThrow();
        repository.deleteByIdStatement(id);
        audit.log("DELETE", "AppUser", id, "Eliminación", http);
        return ApiResponse.ok("Eliminado", null);
    }

    private void apply(AppUser user, UserRequest request, boolean passwordRequired) {
        user.name = request.name();
        user.email = request.email();
        if (request.password() != null && !request.password().isBlank()) {
            user.password = passwordEncoder.encode(request.password());
        } else if (passwordRequired) {
            throw new IllegalArgumentException("La contraseña es obligatoria");
        }
        user.role = request.role();
        if (request.active() != null) {
            user.active = request.active();
        }
    }
}
