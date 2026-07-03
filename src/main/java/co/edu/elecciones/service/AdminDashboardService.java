package co.edu.elecciones.service;

import co.edu.elecciones.domain.AuditEvent;
import co.edu.elecciones.domain.ElectionState;
import co.edu.elecciones.dto.Responses.DashboardActivity;
import co.edu.elecciones.dto.Responses.DashboardAdmin;
import co.edu.elecciones.dto.Responses.DashboardCounters;
import co.edu.elecciones.dto.Responses.DashboardElection;
import co.edu.elecciones.dto.Responses.DashboardSystemStatus;
import co.edu.elecciones.repository.AuditEventRepository;
import co.edu.elecciones.repository.CandidateRepository;
import co.edu.elecciones.repository.ElectionRepository;
import co.edu.elecciones.repository.ElectionResultSummaryRepository;
import co.edu.elecciones.repository.OfficialResultRepository;
import co.edu.elecciones.repository.PartyRepository;
import co.edu.elecciones.repository.PollRepository;
import co.edu.elecciones.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class AdminDashboardService {

    private static final List<ElectionState> ACTIVE_STATES = List.of(
            ElectionState.ABIERTA,
            ElectionState.EN_CONTEO
    );

    private static final Map<String, String> ACTION_TITLES = Map.ofEntries(
            Map.entry("CREATE", "Registro creado"),
            Map.entry("UPDATE", "Registro actualizado"),
            Map.entry("DELETE", "Registro eliminado"),
            Map.entry("IMPORT", "Importación completada"),
            Map.entry("IMPORT_POLLS", "Encuestas importadas"),
            Map.entry("IMPORT_RESULTS", "Resultados importados"),
            Map.entry("SYNC", "Sincronización completada"),
            Map.entry("VERIFY", "Verificación completada"),
            Map.entry("SEED_GEMINI_DEMO", "Datos de prueba cargados"),
            Map.entry("LOGIN", "Inicio de sesión")
    );

    private final ElectionRepository elections;
    private final UserRepository users;
    private final PartyRepository parties;
    private final CandidateRepository candidates;
    private final PollRepository polls;
    private final OfficialResultRepository results;
    private final ElectionResultSummaryRepository summaries;
    private final AuditEventRepository auditEvents;

    public AdminDashboardService(
            ElectionRepository elections,
            UserRepository users,
            PartyRepository parties,
            CandidateRepository candidates,
            PollRepository polls,
            OfficialResultRepository results,
            ElectionResultSummaryRepository summaries,
            AuditEventRepository auditEvents
    ) {
        this.elections = elections;
        this.users = users;
        this.parties = parties;
        this.candidates = candidates;
        this.polls = polls;
        this.results = results;
        this.summaries = summaries;
        this.auditEvents = auditEvents;
    }

    public DashboardAdmin getDashboard() {
        long activeElectionCount = elections.selectActiveCount(ACTIVE_STATES);
        long candidateCount = candidates.selectCount();
        long pollCount = polls.selectCount();
        long userCount = users.selectCount();
        long partyCount = parties.selectCount();
        long auditCount = auditEvents.selectCount();
        long resultCount = results.selectCount();
        long summaryCount = summaries.selectCount();

        DashboardCounters counters = new DashboardCounters(
                activeElectionCount,
                candidateCount,
                pollCount,
                userCount,
                partyCount,
                auditCount,
                resultCount
        );

        List<DashboardElection> dashboardElections = elections.selectDashboardElections(ACTIVE_STATES)
                .stream()
                .map(row -> {
                    int reported = value(row.getReportedTables());
                    int total = value(row.getTotalTables());
                    boolean hasSummary = Boolean.TRUE.equals(row.getSummaryAvailable());
                    Double progress = hasSummary && total > 0
                            ? roundOneDecimal(reported * 100.0 / total)
                            : null;
                    return new DashboardElection(
                            row.getId(),
                            row.getName(),
                            row.getType() == null ? null : row.getType().name(),
                            row.getElectionDate(),
                            row.getState() == null ? null : row.getState().name(),
                            reported,
                            total,
                            progress,
                            hasSummary
                    );
                })
                .toList();

        List<DashboardActivity> recentActivity = auditEvents.selectRecent(PageRequest.of(0, 6))
                .stream()
                .map(this::toActivity)
                .toList();

        long primaryRecords = activeElectionCount + candidateCount + pollCount + userCount
                + partyCount + resultCount;
        List<DashboardSystemStatus> status = List.of(
                new DashboardSystemStatus(
                        "SERVICES",
                        "Disponible",
                        "Los servicios administrativos respondieron correctamente.",
                        "SUCCESS"
                ),
                new DashboardSystemStatus(
                        "DATABASE",
                        "Operativa",
                        primaryRecords + " registros principales disponibles.",
                        "SUCCESS"
                ),
                electoralDataStatus(resultCount, summaryCount)
        );

        return new DashboardAdmin(
                counters,
                dashboardElections,
                recentActivity,
                status,
                Instant.now()
        );
    }

    private DashboardSystemStatus electoralDataStatus(long resultCount, long summaryCount) {
        if (resultCount == 0) {
            return new DashboardSystemStatus(
                    "ELECTORAL_DATA",
                    "Sin resultados",
                    "Aún no hay resultados electorales cargados.",
                    "WARNING"
            );
        }
        if (summaryCount == 0) {
            return new DashboardSystemStatus(
                    "ELECTORAL_DATA",
                    "Carga parcial",
                    resultCount + " resultados disponibles, pero falta el resumen consolidado.",
                    "WARNING"
            );
        }
        return new DashboardSystemStatus(
                "ELECTORAL_DATA",
                "Consolidada",
                resultCount + " resultados y " + summaryCount + " resumen electoral disponible.",
                "SUCCESS"
        );
    }

    private DashboardActivity toActivity(AuditEvent event) {
        String action = normalize(event.action);
        String title = ACTION_TITLES.getOrDefault(action, humanize(action));
        String detail = event.details == null || event.details.isBlank()
                ? entityDetail(event)
                : event.details.trim();
        String actor = event.username == null || event.username.isBlank()
                ? "Sistema"
                : event.username.trim();
        return new DashboardActivity(
                event.id,
                title,
                detail,
                actor,
                event.success,
                event.at
        );
    }

    private String entityDetail(AuditEvent event) {
        String entity = event.entity == null || event.entity.isBlank()
                ? "Sistema"
                : humanize(event.entity);
        return event.entityId == null ? entity : entity + " #" + event.entityId;
    }

    private String humanize(String value) {
        if (value == null || value.isBlank()) {
            return "Actividad del sistema";
        }
        String normalized = value.replace('_', ' ').trim().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private int value(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private double roundOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
