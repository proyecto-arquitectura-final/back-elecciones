package co.edu.elecciones.controller;

import co.edu.elecciones.commons.dto.ApiResponse;
import co.edu.elecciones.dto.Requests.PollRequest;
import co.edu.elecciones.dto.Responses.PollResponse;
import co.edu.elecciones.service.AuditService;
import co.edu.elecciones.service.PollService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/encuestas")
public class EncuestasController {
    private final PollService service;
    private final AuditService audit;

    public EncuestasController(PollService service, AuditService audit) {
        this.service = service;
        this.audit = audit;
    }

    @GetMapping
    public ApiResponse<List<PollResponse>> all() {
        return ApiResponse.ok("OK", service.selectAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<PollResponse> one(@PathVariable Long id) {
        return ApiResponse.ok("OK", service.selectById(id));
    }

    @PostMapping
    public ApiResponse<PollResponse> create(@RequestBody PollRequest request, HttpServletRequest http) {
        PollResponse saved = service.create(request);
        audit.log("CREATE", "Poll", saved.id(), "Creación", http);
        return ApiResponse.ok("Creada", saved);
    }

    @PutMapping("/{id}")
    public ApiResponse<PollResponse> update(@PathVariable Long id, @RequestBody PollRequest request,
                                            HttpServletRequest http) {
        PollResponse saved = service.update(id, request);
        audit.log("UPDATE", "Poll", saved.id(), "Actualización", http);
        return ApiResponse.ok("Actualizada", saved);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest http) {
        service.delete(id);
        audit.log("DELETE", "Poll", id, "Eliminación", http);
        return ApiResponse.ok("Eliminada", null);
    }

    @PostMapping("/import-csv")
    public ApiResponse<Integer> csv(@RequestParam MultipartFile file, HttpServletRequest http) throws Exception {
        int imported = service.importCsv(file);
        audit.log("IMPORT", "Poll", null, "CSV encuestas: " + imported, http);
        return ApiResponse.ok("Importadas", imported);
    }
}
