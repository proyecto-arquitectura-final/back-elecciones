package co.edu.elecciones;

import co.edu.elecciones.config.BusinessConflictException;
import co.edu.elecciones.domain.Election;
import co.edu.elecciones.domain.ElectionRound;
import co.edu.elecciones.domain.ElectionState;
import co.edu.elecciones.domain.ElectionType;
import co.edu.elecciones.dto.Requests.ElectionRequest;
import co.edu.elecciones.repository.ElectionRepository;
import co.edu.elecciones.service.ElectionManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ElectionManagementServiceTest {

    @Mock
    private ElectionRepository elections;

    private ElectionManagementService service;
    private Election election;

    @BeforeEach
    void setUp() {
        service = new ElectionManagementService(elections);
        election = new Election();
        election.id = 1L;
        election.name = "Presidencia Colombia 2026";
        election.type = ElectionType.PRESIDENCIA;
        election.round = ElectionRound.PRIMERA;
        election.electionDate = LocalDate.of(2026, 5, 31);
        election.state = ElectionState.CONFIGURADA;
    }

    @Test
    void managementUsesPersistedSummaryAndDependencyCounts() {
        ElectionRepository.ManagementRow row = row(
                ElectionState.EN_CONTEO,
                true,
                8_200,
                10_000,
                4L,
                32L,
                2L
        );
        when(elections.selectManagementRows()).thenReturn(List.of(row));

        var management = service.getManagement();

        assertEquals(1, management.counters().total());
        assertEquals(1, management.counters().counting());
        assertEquals(82.0, management.elections().get(0).progress());
        assertTrue(management.elections().get(0).structureLocked());
        assertFalse(management.elections().get(0).deletable());
    }

    @Test
    void createsConfiguredPresidentialElection() {
        when(elections.selectDuplicateDefinitionCount(
                "Presidencia Colombia 2030",
                ElectionType.PRESIDENCIA,
                ElectionRound.PRIMERA,
                LocalDate.of(2030, 5, 26),
                null
        )).thenReturn(0L);
        when(elections.save(any(Election.class))).thenAnswer(invocation -> {
            Election saved = invocation.getArgument(0);
            saved.id = 9L;
            return saved;
        });

        var response = service.create(new ElectionRequest(
                "  Presidencia   Colombia 2030 ",
                ElectionType.PRESIDENCIA,
                ElectionRound.PRIMERA,
                LocalDate.of(2030, 5, 26),
                ElectionState.CONFIGURADA
        ));

        assertEquals("Presidencia Colombia 2030", response.name());
        assertEquals("CONFIGURADA", response.state());
    }

    @Test
    void rejectsRoundForNonPresidentialElection() {
        assertThrows(IllegalArgumentException.class, () -> service.create(new ElectionRequest(
                "Senado 2030",
                ElectionType.SENADO,
                ElectionRound.PRIMERA,
                LocalDate.of(2030, 3, 10),
                ElectionState.CONFIGURADA
        )));
    }

    @Test
    void preventsStructuralChangeWhenElectionHasCandidates() {
        when(elections.selectById(1L)).thenReturn(Optional.of(election));
        ElectionRepository.ManagementRow managementRow = row(
                ElectionState.CONFIGURADA,
                false,
                0,
                0,
                4L,
                0L,
                0L
        );
        when(elections.selectManagementRow(1L)).thenReturn(Optional.of(managementRow));
        when(elections.selectDuplicateDefinitionCount(
                "Presidencia Colombia 2026",
                ElectionType.PRESIDENCIA,
                ElectionRound.SEGUNDA,
                LocalDate.of(2026, 6, 21),
                1L
        )).thenReturn(0L);

        assertThrows(BusinessConflictException.class, () -> service.update(1L, new ElectionRequest(
                "Presidencia Colombia 2026",
                ElectionType.PRESIDENCIA,
                ElectionRound.SEGUNDA,
                LocalDate.of(2026, 6, 21),
                ElectionState.CONFIGURADA
        )));
    }

    @Test
    void rejectsCountingWithoutResultsOrSummary() {
        election.state = ElectionState.ABIERTA;
        when(elections.selectById(1L)).thenReturn(Optional.of(election));
        ElectionRepository.ManagementRow managementRow = row(
                ElectionState.ABIERTA,
                false,
                0,
                0,
                0L,
                0L,
                0L
        );
        when(elections.selectManagementRow(1L)).thenReturn(Optional.of(managementRow));
        when(elections.selectDuplicateDefinitionCount(
                election.name,
                election.type,
                election.round,
                election.electionDate,
                1L
        )).thenReturn(0L);

        assertThrows(BusinessConflictException.class, () -> service.update(1L, new ElectionRequest(
                election.name,
                election.type,
                election.round,
                election.electionDate,
                ElectionState.EN_CONTEO
        )));
    }

    @Test
    void deletesConfiguredElectionWithoutDependencies() {
        when(elections.selectById(1L)).thenReturn(Optional.of(election));
        ElectionRepository.ManagementRow managementRow = row(
                ElectionState.CONFIGURADA,
                false,
                0,
                0,
                0L,
                0L,
                0L
        );
        when(elections.selectManagementRow(1L)).thenReturn(Optional.of(managementRow));
        when(elections.deleteByIdStatement(1L)).thenReturn(1);

        service.delete(1L);

        verify(elections).deleteByIdStatement(1L);
    }

    private ElectionRepository.ManagementRow row(
            ElectionState state,
            boolean summary,
            int reported,
            int total,
            long candidates,
            long results,
            long sessions
    ) {
        ElectionRepository.ManagementRow row = org.mockito.Mockito.mock(ElectionRepository.ManagementRow.class);
        lenient().when(row.getId()).thenReturn(1L);
        lenient().when(row.getName()).thenReturn(election.name);
        lenient().when(row.getType()).thenReturn(election.type.name());
        lenient().when(row.getRound()).thenReturn(election.round.name());
        lenient().when(row.getElectionDate()).thenReturn(election.electionDate);
        lenient().when(row.getState()).thenReturn(state.name());
        lenient().when(row.getReportedTables()).thenReturn(reported);
        lenient().when(row.getTotalTables()).thenReturn(total);
        lenient().when(row.getSummaryAvailable()).thenReturn(summary);
        lenient().when(row.getCandidateCount()).thenReturn(candidates);
        lenient().when(row.getOfficialResultCount()).thenReturn(results);
        lenient().when(row.getAssistantSessionCount()).thenReturn(sessions);
        return row;
    }
}
