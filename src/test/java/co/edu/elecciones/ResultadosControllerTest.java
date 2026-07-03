package co.edu.elecciones;

import co.edu.elecciones.controller.ResultadosController;
import co.edu.elecciones.dto.Requests.OfficialResultRequest;
import co.edu.elecciones.dto.Responses.OfficialResultResponse;
import co.edu.elecciones.dto.Responses.ResultImportResponse;
import co.edu.elecciones.dto.Responses.ResultManagement;
import co.edu.elecciones.repository.OfficialResultRepository;
import co.edu.elecciones.service.AuditService;
import co.edu.elecciones.service.PredictionService;
import co.edu.elecciones.service.ResultManagementService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResultadosControllerTest {

    @Mock ResultManagementService service;
    @Mock OfficialResultRepository repository;
    @Mock PredictionService predictions;
    @Mock AuditService audit;
    @Mock HttpServletRequest request;

    @Test
    void managementForwardsAllFiltersAndPagination() {
        ResultManagement response = new ResultManagement(
                null, null, null, null, List.of(), List.of(), List.of(), List.of(), List.of(), null
        );
        when(service.management(2L, null, "Antioquia", "Medellín", "maría", 1, 20))
                .thenReturn(response);

        var result = controller().management(
                2L, null, "Antioquia", "Medellín", "maría", 1, 20
        );

        assertEquals(response, result.data());
        verify(service).management(2L, null, "Antioquia", "Medellín", "maría", 1, 20);
    }

    @Test
    void createReturnsCreatedAndWritesAudit() {
        OfficialResultRequest input = new OfficialResultRequest(
                1L, 3L, "Antioquia", "Medellín", 100L, 8, 10, 65.0, "CARGA_MANUAL"
        );
        OfficialResultResponse saved = new OfficialResultResponse(
                9L, null, null, null, null, "Antioquia", "Medellín",
                100, 100, 8, 10, 65, "CARGA_MANUAL", null,
                "VALIDADO", null, null, null
        );
        when(service.create(input)).thenReturn(saved);

        var response = controller().create(input, request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(9L, response.getBody().data().id());
        verify(audit).log("CREATE", "OfficialResult", 9L, "Creación de resultado oficial", request);
    }

    @Test
    void csvReturnsCreatedAndAuditsCounts() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resultados.csv", "text/csv", "header".getBytes()
        );
        when(service.importCsv(file)).thenReturn(new ResultImportResponse(2, 1, 3));

        var response = controller().importCsv(file, request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(3, response.getBody().data().processed());
        verify(audit).log(
                "IMPORT",
                "OfficialResult",
                null,
                "CSV de resultados: 2 creados, 1 actualizados",
                request
        );
    }

    private ResultadosController controller() {
        return new ResultadosController(service, repository, predictions, audit);
    }
}
