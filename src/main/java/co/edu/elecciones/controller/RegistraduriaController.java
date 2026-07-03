package co.edu.elecciones.controller;

import co.edu.elecciones.commons.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/registraduria")
public class RegistraduriaController {

    @PostMapping("/sincronizar")
    public ResponseEntity<ApiResponse<Void>> sync() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(ApiResponse.error(
                        "La integración automática con la fuente oficial aún no está configurada. "
                                + "Utiliza la carga manual o la importación CSV validada."
                ));
    }

    @GetMapping("/estado")
    public ApiResponse<String> status() {
        return ApiResponse.ok("Estado de la integración oficial", "NO_CONFIGURADA");
    }
}
