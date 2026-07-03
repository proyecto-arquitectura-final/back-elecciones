package co.edu.elecciones.controller;

import co.edu.elecciones.commons.dto.ApiResponse;
import co.edu.elecciones.dto.Responses.DashboardAdmin;
import co.edu.elecciones.service.AdminDashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
public class DashboardController {

    private final AdminDashboardService service;

    public DashboardController(AdminDashboardService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<DashboardAdmin> get() {
        return ApiResponse.ok("Panel administrativo actualizado", service.getDashboard());
    }
}
