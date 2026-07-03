package co.edu.elecciones;

import co.edu.elecciones.domain.AuditEvent;
import co.edu.elecciones.domain.ElectionState;
import co.edu.elecciones.domain.ElectionType;
import co.edu.elecciones.dto.Responses.DashboardAdmin;
import co.edu.elecciones.repository.AuditEventRepository;
import co.edu.elecciones.repository.CandidateRepository;
import co.edu.elecciones.repository.ElectionRepository;
import co.edu.elecciones.repository.ElectionResultSummaryRepository;
import co.edu.elecciones.repository.OfficialResultRepository;
import co.edu.elecciones.repository.PartyRepository;
import co.edu.elecciones.repository.PollRepository;
import co.edu.elecciones.repository.UserRepository;
import co.edu.elecciones.service.AdminDashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock ElectionRepository elections;
    @Mock UserRepository users;
    @Mock PartyRepository parties;
    @Mock CandidateRepository candidates;
    @Mock PollRepository polls;
    @Mock OfficialResultRepository results;
    @Mock ElectionResultSummaryRepository summaries;
    @Mock AuditEventRepository auditEvents;

    private AdminDashboardService service;

    @BeforeEach
    void setUp() {
        service = new AdminDashboardService(
                elections,
                users,
                parties,
                candidates,
                polls,
                results,
                summaries,
                auditEvents
        );
    }

    @Test
    void buildsDashboardFromPersistedAggregates() {
        when(elections.selectActiveCount(any())).thenReturn(1L);
        when(candidates.selectCount()).thenReturn(4L);
        when(polls.selectCount()).thenReturn(5L);
        when(users.selectCount()).thenReturn(2L);
        when(parties.selectCount()).thenReturn(4L);
        when(auditEvents.selectCount()).thenReturn(3L);
        when(results.selectCount()).thenReturn(32L);
        when(summaries.selectCount()).thenReturn(1L);

        ElectionRepository.DashboardElectionRow row = mock(ElectionRepository.DashboardElectionRow.class);
        when(row.getId()).thenReturn(1L);
        when(row.getName()).thenReturn("Presidencia Colombia 2026 - Primera vuelta");
        when(row.getType()).thenReturn(ElectionType.PRESIDENCIA);
        when(row.getElectionDate()).thenReturn(LocalDate.of(2026, 5, 31));
        when(row.getState()).thenReturn(ElectionState.EN_CONTEO);
        when(row.getReportedTables()).thenReturn(8200);
        when(row.getTotalTables()).thenReturn(10000);
        when(row.getSummaryAvailable()).thenReturn(true);
        when(elections.selectDashboardElections(any())).thenReturn(List.of(row));

        AuditEvent event = new AuditEvent();
        event.id = 10L;
        event.action = "IMPORT_RESULTS";
        event.details = "Se cargaron resultados electorales.";
        event.username = "sistema";
        event.success = true;
        event.at = Instant.parse("2026-07-02T20:00:00Z");
        when(auditEvents.selectRecent(any(Pageable.class))).thenReturn(List.of(event));

        DashboardAdmin dashboard = service.getDashboard();

        assertEquals(1L, dashboard.counters().activeElections());
        assertEquals(5L, dashboard.counters().polls());
        assertEquals(82.0, dashboard.elections().get(0).progress());
        assertTrue(dashboard.elections().get(0).summaryAvailable());
        assertEquals("Resultados importados", dashboard.recentActivity().get(0).title());
        assertEquals("Consolidada", dashboard.systemStatus().get(2).status());
    }

    @Test
    void reportsMissingElectoralDataWithoutInventingAvailability() {
        when(elections.selectActiveCount(any())).thenReturn(0L);
        when(candidates.selectCount()).thenReturn(0L);
        when(polls.selectCount()).thenReturn(0L);
        when(users.selectCount()).thenReturn(1L);
        when(parties.selectCount()).thenReturn(0L);
        when(auditEvents.selectCount()).thenReturn(0L);
        when(results.selectCount()).thenReturn(0L);
        when(summaries.selectCount()).thenReturn(0L);
        when(elections.selectDashboardElections(any())).thenReturn(List.of());
        when(auditEvents.selectRecent(any(Pageable.class))).thenReturn(List.of());

        DashboardAdmin dashboard = service.getDashboard();

        assertEquals("Sin resultados", dashboard.systemStatus().get(2).status());
        assertEquals("WARNING", dashboard.systemStatus().get(2).level());
        assertTrue(dashboard.elections().isEmpty());
    }
}
