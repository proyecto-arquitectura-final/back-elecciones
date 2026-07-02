package co.edu.elecciones.controller;

import co.edu.elecciones.commons.dto.ApiResponse;
import co.edu.elecciones.domain.ElectionState;
import co.edu.elecciones.dto.Responses.DashboardAdmin;
import co.edu.elecciones.repository.AuditEventRepository;
import co.edu.elecciones.repository.CandidateRepository;
import co.edu.elecciones.repository.ElectionRepository;
import co.edu.elecciones.repository.PartyRepository;
import co.edu.elecciones.repository.UserRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
public class DashboardController {
    private final ElectionRepository elections;
    private final UserRepository users;
    private final PartyRepository parties;
    private final CandidateRepository candidates;
    private final AuditEventRepository auditEvents;

    public DashboardController(ElectionRepository elections, UserRepository users, PartyRepository parties,
                               CandidateRepository candidates, AuditEventRepository auditEvents) {
        this.elections = elections;
        this.users = users;
        this.parties = parties;
        this.candidates = candidates;
        this.auditEvents = auditEvents;
    }

    @GetMapping
    public ApiResponse<DashboardAdmin> get() {
        return ApiResponse.ok("OK", new DashboardAdmin(
                elections.selectActiveCount(List.of(ElectionState.ABIERTA, ElectionState.EN_CONTEO)),
                users.selectCount(),
                parties.selectCount(),
                candidates.selectCount(),
                auditEvents.selectCount()
        ));
    }
}
