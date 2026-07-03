package co.edu.elecciones.service;

import co.edu.elecciones.config.BusinessConflictException;
import co.edu.elecciones.domain.Election;
import co.edu.elecciones.domain.ElectionRound;
import co.edu.elecciones.domain.ElectionState;
import co.edu.elecciones.domain.ElectionType;
import co.edu.elecciones.dto.Requests.ElectionRequest;
import co.edu.elecciones.dto.Responses.ElectionManagement;
import co.edu.elecciones.dto.Responses.ElectionManagementCounters;
import co.edu.elecciones.dto.Responses.ElectionManagementItem;
import co.edu.elecciones.dto.Responses.ElectionResponse;
import co.edu.elecciones.repository.ElectionRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ElectionManagementService {
    private static final Map<ElectionState, Set<ElectionState>> STATE_TRANSITIONS = transitions();

    private final ElectionRepository elections;

    public ElectionManagementService(ElectionRepository elections) {
        this.elections = elections;
    }

    @Transactional(readOnly = true)
    public ElectionManagement getManagement() {
        List<ElectionManagementItem> rows = elections.selectManagementRows().stream()
                .map(this::toManagementItem)
                .toList();

        ElectionManagementCounters counters = new ElectionManagementCounters(
                rows.size(),
                count(rows, ElectionState.CONFIGURADA),
                count(rows, ElectionState.ABIERTA),
                count(rows, ElectionState.EN_CONTEO),
                count(rows, ElectionState.CERRADA),
                count(rows, ElectionState.ARCHIVADA),
                rows.stream().filter(ElectionManagementItem::summaryAvailable).count(),
                rows.stream().filter(item -> !item.summaryAvailable()).count()
        );

        return new ElectionManagement(counters, rows, Instant.now());
    }

    @Transactional(readOnly = true)
    public List<ElectionResponse> selectAll() {
        return elections.selectAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ElectionResponse selectById(Long id) {
        return toResponse(findElection(id));
    }

    @Transactional
    public ElectionResponse create(ElectionRequest request) {
        validateDefinition(request, null);
        if (request.state() != ElectionState.CONFIGURADA) {
            throw new IllegalArgumentException("Una elección nueva debe iniciar en estado Configurada");
        }

        Election election = new Election();
        apply(election, request);
        return toResponse(elections.save(election));
    }

    @Transactional
    public ElectionResponse update(Long id, ElectionRequest request) {
        Election election = findElection(id);
        ElectionRepository.ManagementRow current = findManagementRow(id);

        validateDefinition(request, id);
        validateStructuralChanges(election, request, current);
        validateStateTransition(election.state, request.state(), current);

        apply(election, request);
        return toResponse(elections.save(election));
    }

    @Transactional
    public void delete(Long id) {
        Election election = findElection(id);
        ElectionRepository.ManagementRow row = findManagementRow(id);

        if (!isDeletable(row)) {
            throw new BusinessConflictException(deletionConflictMessage(election, row));
        }

        if (elections.deleteByIdStatement(id) != 1) {
            throw new EntityNotFoundException("No existe la elección solicitada");
        }
    }

    private void validateDefinition(ElectionRequest request, Long excludeId) {
        String name = normalizeRequired(request.name());
        validateRound(request.type(), request.round());

        if (elections.selectDuplicateDefinitionCount(
                name,
                request.type(),
                request.round(),
                request.electionDate(),
                excludeId
        ) > 0) {
            throw new BusinessConflictException(
                    "Ya existe una elección con el mismo nombre, tipo, ronda y fecha"
            );
        }
    }

    private void validateRound(ElectionType type, ElectionRound round) {
        if (type == ElectionType.PRESIDENCIA && round == ElectionRound.NINGUNA) {
            throw new IllegalArgumentException("Las elecciones presidenciales deben indicar primera o segunda vuelta");
        }
        if (type != ElectionType.PRESIDENCIA && round != ElectionRound.NINGUNA) {
            throw new IllegalArgumentException("Senado y Cámara no utilizan rondas presidenciales");
        }
    }

    private void validateStructuralChanges(
            Election election,
            ElectionRequest request,
            ElectionRepository.ManagementRow row
    ) {
        if (!isStructureLocked(row)) {
            return;
        }

        boolean changed = election.type != request.type()
                || election.round != request.round()
                || !election.electionDate.equals(request.electionDate());

        if (changed) {
            throw new BusinessConflictException(
                    "No se puede cambiar el tipo, la ronda o la fecha porque la elección ya tiene candidatos o resultados asociados"
            );
        }
    }

    private void validateStateTransition(
            ElectionState current,
            ElectionState requested,
            ElectionRepository.ManagementRow row
    ) {
        if (current == requested) {
            return;
        }

        if (!STATE_TRANSITIONS.getOrDefault(current, Set.of()).contains(requested)) {
            throw new BusinessConflictException(
                    "No se permite cambiar el estado de " + label(current) + " a " + label(requested)
            );
        }

        if (requested == ElectionState.EN_CONTEO
                && !Boolean.TRUE.equals(row.getSummaryAvailable())
                && safe(row.getOfficialResultCount()) == 0) {
            throw new BusinessConflictException(
                    "La elección necesita un resumen o resultados cargados antes de pasar a En conteo"
            );
        }
    }

    private void apply(Election election, ElectionRequest request) {
        election.name = normalizeRequired(request.name());
        election.type = request.type();
        election.round = request.round();
        election.electionDate = request.electionDate();
        election.state = request.state();
    }

    private Election findElection(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("El identificador de la elección es obligatorio");
        }
        return elections.selectById(id)
                .orElseThrow(() -> new EntityNotFoundException("No existe la elección solicitada"));
    }

    private ElectionRepository.ManagementRow findManagementRow(Long id) {
        return elections.selectManagementRow(id)
                .orElseThrow(() -> new EntityNotFoundException("No existe la elección solicitada"));
    }

    private ElectionResponse toResponse(Election election) {
        return new ElectionResponse(
                election.id,
                election.createdAt,
                election.updatedAt,
                election.name,
                election.type == null ? null : election.type.name(),
                election.round == null ? null : election.round.name(),
                election.electionDate,
                election.state == null ? null : election.state.name()
        );
    }

    private ElectionManagementItem toManagementItem(ElectionRepository.ManagementRow row) {
        ElectionState state = ElectionState.valueOf(row.getState());
        int reported = safe(row.getReportedTables());
        int total = safe(row.getTotalTables());
        boolean summary = Boolean.TRUE.equals(row.getSummaryAvailable());
        boolean locked = isStructureLocked(row);

        return new ElectionManagementItem(
                row.getId(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                row.getName(),
                row.getType(),
                row.getRound(),
                row.getElectionDate(),
                row.getState(),
                reported,
                total,
                summary ? percentage(reported, total) : null,
                summary,
                safe(row.getCandidateCount()),
                safe(row.getOfficialResultCount()),
                safe(row.getAssistantSessionCount()),
                locked,
                isDeletable(row),
                allowedStates(state, row)
        );
    }

    private List<String> allowedStates(ElectionState state, ElectionRepository.ManagementRow row) {
        return STATE_TRANSITIONS.getOrDefault(state, Set.of()).stream()
                .filter(next -> next != ElectionState.EN_CONTEO
                        || Boolean.TRUE.equals(row.getSummaryAvailable())
                        || safe(row.getOfficialResultCount()) > 0)
                .map(Enum::name)
                .sorted()
                .toList();
    }

    private boolean isStructureLocked(ElectionRepository.ManagementRow row) {
        return safe(row.getCandidateCount()) > 0
                || safe(row.getOfficialResultCount()) > 0
                || Boolean.TRUE.equals(row.getSummaryAvailable());
    }

    private boolean isDeletable(ElectionRepository.ManagementRow row) {
        ElectionState state = ElectionState.valueOf(row.getState());
        boolean stateAllowsDelete = state == ElectionState.CONFIGURADA || state == ElectionState.ARCHIVADA;
        return stateAllowsDelete
                && safe(row.getCandidateCount()) == 0
                && safe(row.getOfficialResultCount()) == 0
                && safe(row.getAssistantSessionCount()) == 0
                && !Boolean.TRUE.equals(row.getSummaryAvailable());
    }

    private String deletionConflictMessage(Election election, ElectionRepository.ManagementRow row) {
        if (election.state != ElectionState.CONFIGURADA && election.state != ElectionState.ARCHIVADA) {
            return "Solo se pueden eliminar elecciones configuradas o archivadas";
        }
        return "No se puede eliminar la elección porque tiene candidatos, resultados, resumen electoral o conversaciones asociadas. Puedes archivarla para conservar la trazabilidad.";
    }

    private long count(List<ElectionManagementItem> rows, ElectionState state) {
        return rows.stream().filter(item -> state.name().equals(item.state())).count();
    }

    private String normalizeRequired(String value) {
        if (value == null) {
            throw new IllegalArgumentException("El nombre de la elección es obligatorio");
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("El nombre de la elección es obligatorio");
        }
        return normalized;
    }

    private double percentage(int reported, int total) {
        if (total <= 0) {
            return 0.0;
        }
        return Math.round((reported * 1000.0) / total) / 10.0;
    }

    private long safe(Long value) {
        return value == null ? 0L : value;
    }

    private int safe(Integer value) {
        return value == null ? 0 : value;
    }

    private String label(ElectionState state) {
        return switch (state) {
            case CONFIGURADA -> "Configurada";
            case ABIERTA -> "Abierta";
            case EN_CONTEO -> "En conteo";
            case CERRADA -> "Cerrada";
            case ARCHIVADA -> "Archivada";
        };
    }

    private static Map<ElectionState, Set<ElectionState>> transitions() {
        EnumMap<ElectionState, Set<ElectionState>> transitions = new EnumMap<>(ElectionState.class);
        transitions.put(ElectionState.CONFIGURADA, Set.of(ElectionState.ABIERTA, ElectionState.ARCHIVADA));
        transitions.put(ElectionState.ABIERTA, Set.of(ElectionState.EN_CONTEO, ElectionState.CERRADA));
        transitions.put(ElectionState.EN_CONTEO, Set.of(ElectionState.CERRADA));
        transitions.put(ElectionState.CERRADA, Set.of(ElectionState.ARCHIVADA));
        transitions.put(ElectionState.ARCHIVADA, Set.of());
        return Map.copyOf(transitions);
    }
}
