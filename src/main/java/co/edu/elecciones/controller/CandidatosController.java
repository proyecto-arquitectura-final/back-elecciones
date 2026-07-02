package co.edu.elecciones.controller;

import co.edu.elecciones.commons.dto.ApiResponse;
import co.edu.elecciones.domain.Candidate;
import co.edu.elecciones.dto.Requests.CandidateRequest;
import co.edu.elecciones.repository.CandidateRepository;
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
@RequestMapping("/api/v1/candidatos")
public class CandidatosController {
    private final CandidateRepository repository;
    private final PartyRepository parties;
    private final AuditService audit;

    public CandidatosController(CandidateRepository repository, PartyRepository parties, AuditService audit) {
        this.repository = repository;
        this.parties = parties;
        this.audit = audit;
    }

    @GetMapping
    public ApiResponse<List<Candidate>> all() {
        return ApiResponse.ok("OK", repository.selectAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<Candidate> one(@PathVariable Long id) {
        return ApiResponse.ok("OK", repository.selectById(id).orElseThrow());
    }

    @PostMapping
    public ApiResponse<Candidate> create(@RequestBody CandidateRequest request, HttpServletRequest http) {
        Candidate candidate = new Candidate();
        apply(candidate, request);
        Candidate saved = repository.save(candidate);
        audit.log("CREATE", "Candidate", saved.id, "Creación", http);
        return ApiResponse.ok("Creado", saved);
    }

    @PutMapping("/{id}")
    public ApiResponse<Candidate> update(@PathVariable Long id, @RequestBody CandidateRequest request,
                                         HttpServletRequest http) {
        Candidate candidate = repository.selectById(id).orElseThrow();
        apply(candidate, request);
        Candidate saved = repository.save(candidate);
        audit.log("UPDATE", "Candidate", saved.id, "Actualización", http);
        return ApiResponse.ok("Actualizado", saved);
    }

    @Transactional
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest http) {
        repository.selectById(id).orElseThrow();
        repository.deleteByIdStatement(id);
        audit.log("DELETE", "Candidate", id, "Eliminación", http);
        return ApiResponse.ok("Eliminado", null);
    }

    private void apply(Candidate candidate, CandidateRequest request) {
        candidate.name = request.name();
        candidate.vicePresidentName = request.vicePresidentName();
        candidate.party = parties.selectById(request.partyId()).orElseThrow();
        candidate.electionType = request.electionType();
        candidate.department = request.department();
        candidate.municipality = request.municipality();
        if (request.active() != null) {
            candidate.active = request.active();
        }
    }
}
