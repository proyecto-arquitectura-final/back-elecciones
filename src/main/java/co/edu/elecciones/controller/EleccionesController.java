package co.edu.elecciones.controller;

import co.edu.elecciones.commons.dto.ApiResponse;
import co.edu.elecciones.domain.Election;
import co.edu.elecciones.dto.Requests.ElectionRequest;
import co.edu.elecciones.repository.ElectionRepository;
import co.edu.elecciones.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
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
@RequestMapping("/api/v1/elecciones")
public class EleccionesController {
    private final ElectionRepository repository;
    private final AuditService audit;

    public EleccionesController(ElectionRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    @GetMapping
    public ApiResponse<List<Election>> all() {
        return ApiResponse.ok("OK", repository.selectAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<Election> one(@PathVariable Long id) {
        return ApiResponse.ok("OK", repository.selectById(id).orElseThrow());
    }

    @PostMapping
    public ApiResponse<Election> create(@RequestBody ElectionRequest request, HttpServletRequest http) {
        Election election = new Election();
        apply(election, request);
        Election saved = repository.save(election);
        audit.log("CREATE", "Election", saved.id, "Creación", http);
        return ApiResponse.ok("Creado", saved);
    }

    @PutMapping("/{id}")
    public ApiResponse<Election> update(@PathVariable Long id, @RequestBody ElectionRequest request,
                                        HttpServletRequest http) {
        Election election = repository.selectById(id).orElseThrow();
        apply(election, request);
        Election saved = repository.save(election);
        audit.log("UPDATE", "Election", saved.id, "Actualización", http);
        return ApiResponse.ok("Actualizado", saved);
    }

    @Transactional
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest http) {
        repository.selectById(id).orElseThrow();
        repository.deleteByIdStatement(id);
        audit.log("DELETE", "Election", id, "Eliminación", http);
        return ApiResponse.ok("Eliminado", null);
    }

    private void apply(Election election, ElectionRequest request) {
        election.name = request.name();
        election.type = request.type();
        election.round = request.round();
        election.electionDate = request.electionDate();
        election.state = request.state();
    }
}
