package co.edu.elecciones.controller;

import co.edu.elecciones.commons.dto.ApiResponse;
import co.edu.elecciones.domain.Party;
import co.edu.elecciones.dto.Requests.PartyRequest;
import co.edu.elecciones.repository.PartyRepository;
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
@RequestMapping("/api/v1/partidos")
public class PartidosController {
    private final PartyRepository repository;
    private final AuditService audit;

    public PartidosController(PartyRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    @GetMapping
    public ApiResponse<List<Party>> all() {
        return ApiResponse.ok("OK", repository.selectAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<Party> one(@PathVariable Long id) {
        return ApiResponse.ok("OK", repository.selectById(id).orElseThrow());
    }

    @PostMapping
    public ApiResponse<Party> create(@RequestBody PartyRequest request, HttpServletRequest http) {
        Party party = new Party();
        apply(party, request);
        Party saved = repository.save(party);
        audit.log("CREATE", "Party", saved.id, "Creación", http);
        return ApiResponse.ok("Creado", saved);
    }

    @PutMapping("/{id}")
    public ApiResponse<Party> update(@PathVariable Long id, @RequestBody PartyRequest request, HttpServletRequest http) {
        Party party = repository.selectById(id).orElseThrow();
        apply(party, request);
        Party saved = repository.save(party);
        audit.log("UPDATE", "Party", saved.id, "Actualización", http);
        return ApiResponse.ok("Actualizado", saved);
    }

    @Transactional
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest http) {
        repository.selectById(id).orElseThrow();
        repository.deleteByIdStatement(id);
        audit.log("DELETE", "Party", id, "Eliminación", http);
        return ApiResponse.ok("Eliminado", null);
    }

    private void apply(Party party, PartyRequest request) {
        party.name = request.name();
        party.acronym = request.acronym();
        party.color = request.color();
        party.foundationYear = request.foundationYear();
        if (request.active() != null) {
            party.active = request.active();
        }
    }
}
