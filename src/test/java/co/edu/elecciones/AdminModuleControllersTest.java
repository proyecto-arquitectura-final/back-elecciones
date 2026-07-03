package co.edu.elecciones;

import co.edu.elecciones.controller.AuditoriaController;
import co.edu.elecciones.controller.ReportesController;
import co.edu.elecciones.controller.UsuariosController;
import co.edu.elecciones.domain.ReportFormat;
import co.edu.elecciones.domain.Role;
import co.edu.elecciones.dto.AdminDtos.AuditCounters;
import co.edu.elecciones.dto.AdminDtos.AuditManagement;
import co.edu.elecciones.dto.AdminDtos.AuditPage;
import co.edu.elecciones.dto.AdminDtos.ReportManagement;
import co.edu.elecciones.dto.AdminDtos.UserCounters;
import co.edu.elecciones.dto.AdminDtos.UserManagement;
import co.edu.elecciones.dto.AdminDtos.UserPage;
import co.edu.elecciones.dto.AdminDtos.UserResponse;
import co.edu.elecciones.dto.Requests.UserRequest;
import co.edu.elecciones.service.AuditManagementService;
import co.edu.elecciones.service.ReportService;
import co.edu.elecciones.service.UserManagementService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminModuleControllersTest {

    @Mock AuditManagementService auditManagementService;
    @Mock UserManagementService userManagementService;
    @Mock ReportService reportService;
    @Mock HttpServletRequest request;

    @Test
    void auditManagementForwardsFiltersAndPagination() {
        AuditManagement management = new AuditManagement(
                new AuditCounters(1, 1, 0, 1),
                new AuditPage(List.of(), 2, 25, 0, 0),
                List.of("LOGIN"),
                List.of("AppUser"),
                Instant.now()
        );
        when(auditManagementService.getManagement(
                "admin", "LOGIN", "AppUser", true, 2, 25
        )).thenReturn(management);

        var response = new AuditoriaController(auditManagementService).management(
                "admin", "LOGIN", "AppUser", true, 2, 25
        );

        assertEquals(management, response.data());
        verify(auditManagementService).getManagement(
                "admin", "LOGIN", "AppUser", true, 2, 25
        );
    }

    @Test
    void userCreateReturnsServiceDto() {
        UserRequest input = new UserRequest(
                "Administradora Electoral",
                "admin@example.com",
                "Password123!",
                Role.ADMINISTRADOR,
                true
        );
        UserResponse saved = new UserResponse(
                7L,
                Instant.now(),
                Instant.now(),
                null,
                "Administradora Electoral",
                "admin@example.com",
                "ADMINISTRADOR",
                true
        );
        when(userManagementService.create(input, request)).thenReturn(saved);

        var response = new UsuariosController(userManagementService).create(input, request);

        assertEquals(7L, response.data().id());
        assertEquals("admin@example.com", response.data().email());
        verify(userManagementService).create(input, request);
    }

    @Test
    void userManagementForwardsSearchAndPage() {
        UserManagement management = new UserManagement(
                new UserCounters(2, 2, 1, 1),
                new UserPage(List.of(), 1, 10, 2, 1),
                Instant.now()
        );
        when(userManagementService.getManagement("analista", 1, 10)).thenReturn(management);

        var response = new UsuariosController(userManagementService).management("analista", 1, 10);

        assertEquals(management, response.data());
        verify(userManagementService).getManagement("analista", 1, 10);
    }

    @Test
    void reportManagementReturnsDashboardData() {
        ReportManagement management = new ReportManagement(
                4L,
                "Presidencia 2026",
                null,
                List.of(),
                List.of(),
                Map.of(),
                Instant.now()
        );
        when(reportService.management(4L)).thenReturn(management);

        var response = new ReportesController(reportService).management(4L);

        assertEquals(management, response.data());
        verify(reportService).management(4L);
    }

    @Test
    void reportDownloadSetsFilenameContentTypeAndBody() {
        byte[] content = "region,votes\nAntioquia,100".getBytes();
        when(reportService.generate(5L, "csv", request)).thenReturn(
                new ReportService.GeneratedReport(
                        content,
                        ReportFormat.CSV,
                        "resultados-eleccion-5.csv"
                )
        );

        var response = new ReportesController(reportService).results(5L, "csv", request);

        assertEquals(MediaType.parseMediaType("text/csv;charset=UTF-8"), response.getHeaders().getContentType());
        assertEquals("attachment; filename=\"resultados-eleccion-5.csv\"",
                response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertEquals(content.length, response.getHeaders().getContentLength());
        assertEquals(content, response.getBody());
        verify(reportService).generate(5L, "csv", request);
    }
}
