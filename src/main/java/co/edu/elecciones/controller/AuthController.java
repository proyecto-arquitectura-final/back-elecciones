package co.edu.elecciones.controller;

import co.edu.elecciones.commons.dto.ApiResponse;
import co.edu.elecciones.commons.security.JwtService;
import co.edu.elecciones.dto.AuthDtos.LoginRequest;
import co.edu.elecciones.dto.AuthDtos.LoginResponse;
import co.edu.elecciones.repository.UserRepository;
import co.edu.elecciones.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository users;
    private final AuditService audit;

    public AuthController(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            UserRepository users,
            AuditService audit
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.users = users;
        this.audit = audit;
    }

    @PostMapping("/login")
    @Transactional
    public ApiResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest http
    ) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        String token = jwtService.generate((UserDetails) authentication.getPrincipal());
        Instant loginAt = Instant.now();
        users.updateLastLoginAt(request.email(), loginAt);
        var user = users.selectByEmail(request.email()).orElseThrow();
        audit.log("LOGIN", "AppUser", user.id, "Inicio de sesión exitoso", http);
        return ApiResponse.ok(
                "Login exitoso",
                new LoginResponse(token, "Bearer", user.id, user.name, user.email, user.role)
        );
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.ok("Logout lógico: elimina el token del frontend", null);
    }
}
