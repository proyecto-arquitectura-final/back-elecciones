package co.edu.elecciones.controller;

import co.edu.elecciones.commons.dto.ApiResponse;
import co.edu.elecciones.dto.AdminDtos.UserManagement;
import co.edu.elecciones.dto.AdminDtos.UserResponse;
import co.edu.elecciones.dto.Requests.UserRequest;
import co.edu.elecciones.service.UserManagementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/usuarios")
public class UsuariosController {
    private final UserManagementService service;

    public UsuariosController(UserManagementService service) {
        this.service = service;
    }

    @GetMapping("/gestion")
    public ApiResponse<UserManagement> management(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.ok("Usuarios consultados", service.getManagement(search, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserResponse> one(@PathVariable Long id) {
        return ApiResponse.ok("Usuario consultado", service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> create(
            @Valid @RequestBody UserRequest request,
            HttpServletRequest http
    ) {
        return ApiResponse.ok("Usuario creado", service.create(request, http));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UserRequest request,
            HttpServletRequest http
    ) {
        return ApiResponse.ok("Usuario actualizado", service.update(id, request, http));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest http) {
        service.delete(id, http);
        return ApiResponse.ok("Usuario eliminado", null);
    }
}
