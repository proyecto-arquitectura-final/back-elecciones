package co.edu.elecciones;

import co.edu.elecciones.controller.EncuestasController;
import co.edu.elecciones.domain.PollStatus;
import co.edu.elecciones.dto.Requests.PollRequest;
import co.edu.elecciones.dto.Requests.PollResultRequest;
import co.edu.elecciones.dto.Responses.PollImportResponse;
import co.edu.elecciones.dto.Responses.PollManagement;
import co.edu.elecciones.dto.Responses.PollResponse;
import co.edu.elecciones.service.AuditService;
import co.edu.elecciones.service.PollService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EncuestasControllerTest {

    @Mock PollService service;
    @Mock AuditService audit;
    @Mock HttpServletRequest httpRequest;

    @Test
    void createReturnsCreatedAndWritesAudit() {
        PollRequest input = new PollRequest(
                1L,
                "Firma electoral",
                LocalDate.of(2026, 4, 20),
                1_500,
                2.5,
                "Muestreo estratificado",
                PollStatus.PENDIENTE,
                List.of(new PollResultRequest(3L, 40.0))
        );
        PollResponse response = new PollResponse(
                9L, null, null, null, "Firma electoral",
                input.date(), input.sampleSize(), input.marginError(), input.methodology(),
                "PENDIENTE", 40.0, List.of()
        );
        when(service.create(input)).thenReturn(response);

        var result = new EncuestasController(service, audit).create(input, httpRequest);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertEquals(9L, result.getBody().data().id());
        verify(audit).log("CREATE", "Poll", 9L, "Creación de encuesta", httpRequest);
    }

    @Test
    void managementForwardsFiltersAndPagination() {
        PollManagement response = new PollManagement(null, null, List.of(), List.of(), null);
        when(service.management(2L, PollStatus.APROBADA, "firma", 1, 20)).thenReturn(response);

        var result = new EncuestasController(service, audit)
                .management(2L, PollStatus.APROBADA, "firma", 1, 20);

        assertEquals(response, result.data());
        verify(service).management(2L, PollStatus.APROBADA, "firma", 1, 20);
    }

    @Test
    void csvReturnsCreatedAndAuditsImportedCounts() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "encuestas.csv", "text/csv", "header".getBytes()
        );
        when(service.importCsv(file)).thenReturn(new PollImportResponse(2, 8));

        var result = new EncuestasController(service, audit).csv(file, httpRequest);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertEquals(2, result.getBody().data().polls());
        verify(audit).log(
                "IMPORT", "Poll", null,
                "CSV de encuestas: 2 encuestas y 8 resultados",
                httpRequest
        );
    }
}
