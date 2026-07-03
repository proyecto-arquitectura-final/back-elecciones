package co.edu.elecciones;

import co.edu.elecciones.controller.CandidatosController;
import co.edu.elecciones.domain.ElectionType;
import co.edu.elecciones.dto.Requests.CandidateRequest;
import co.edu.elecciones.dto.Responses.CandidateElection;
import co.edu.elecciones.dto.Responses.CandidateParty;
import co.edu.elecciones.dto.Responses.CandidateResponse;
import co.edu.elecciones.service.AuditService;
import co.edu.elecciones.service.CandidateManagementService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandidatosControllerTest {

    @Mock CandidateManagementService service;
    @Mock AuditService audit;
    @Mock HttpServletRequest request;

    @Test
    void createReturnsDtoAndWritesAudit() {
        CandidateRequest input = new CandidateRequest(
                "María Fernández",
                "Carlos Rojas",
                1L,
                2L,
                ElectionType.PRESIDENCIA,
                null,
                null,
                true
        );
        CandidateResponse response = new CandidateResponse(
                10L,
                Instant.now(),
                Instant.now(),
                "María Fernández",
                "Carlos Rojas",
                new CandidateParty(1L, "Partido", "P", "#2563EB", true),
                new CandidateElection(
                        2L,
                        "Presidencia 2026",
                        "PRESIDENCIA",
                        "PRIMERA",
                        LocalDate.of(2026, 5, 31),
                        "CONFIGURADA"
                ),
                "PRESIDENCIA",
                null,
                null,
                true,
                0,
                0,
                true
        );
        when(service.create(input)).thenReturn(response);

        var apiResponse = new CandidatosController(service, audit).create(input, request);

        assertEquals(10L, apiResponse.data().id());
        assertEquals("María Fernández", apiResponse.data().name());
        verify(audit).log("CREATE", "Candidate", 10L, "Candidato creado: María Fernández", request);
    }
}
