package co.edu.elecciones.service;

import co.edu.elecciones.domain.AuditEvent;
import co.edu.elecciones.dto.AdminDtos.AuditCounters;
import co.edu.elecciones.dto.AdminDtos.AuditEventResponse;
import co.edu.elecciones.dto.AdminDtos.AuditManagement;
import co.edu.elecciones.dto.AdminDtos.AuditPage;
import co.edu.elecciones.repository.AuditEventRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuditManagementService {
    private final AuditEventRepository repository;

    public AuditManagementService(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public AuditManagement getManagement(
            String search,
            String action,
            String entity,
            Boolean success,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 5), 100);
        var events = repository.selectPage(
                normalize(search),
                normalize(action),
                normalize(entity),
                success,
                PageRequest.of(safePage, safeSize)
        );
        var aggregate = repository.selectAggregate();
        AuditCounters counters = aggregate == null
                ? new AuditCounters(0, 0, 0, 0)
                : new AuditCounters(
                        safeLong(aggregate.getTotal()),
                        safeLong(aggregate.getSuccessful()),
                        safeLong(aggregate.getFailed()),
                        safeLong(aggregate.getUsers())
                );
        AuditPage responsePage = new AuditPage(
                events.getContent().stream().map(this::toResponse).toList(),
                events.getNumber(),
                events.getSize(),
                events.getTotalElements(),
                events.getTotalPages()
        );
        return new AuditManagement(
                counters,
                responsePage,
                repository.selectActions(),
                repository.selectEntities(),
                Instant.now()
        );
    }

    private AuditEventResponse toResponse(AuditEvent event) {
        return new AuditEventResponse(
                event.id,
                event.at,
                defaultText(event.username, "sistema"),
                defaultText(event.action, "SIN_ACCION"),
                defaultText(event.entity, "SISTEMA"),
                event.entityId,
                defaultText(event.details, "Sin detalle"),
                defaultText(event.ip, "No disponible"),
                event.success
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }
}
