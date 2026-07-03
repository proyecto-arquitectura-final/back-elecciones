package co.edu.elecciones;

import co.edu.elecciones.config.BusinessConflictException;
import co.edu.elecciones.domain.Candidate;
import co.edu.elecciones.domain.Election;
import co.edu.elecciones.domain.ElectionResultSummary;
import co.edu.elecciones.domain.ElectionRound;
import co.edu.elecciones.domain.ElectionState;
import co.edu.elecciones.domain.ElectionType;
import co.edu.elecciones.domain.OfficialResult;
import co.edu.elecciones.domain.Party;
import co.edu.elecciones.domain.ResultValidationStatus;
import co.edu.elecciones.dto.Requests.ElectionResultSummaryRequest;
import co.edu.elecciones.dto.Requests.OfficialResultRequest;
import co.edu.elecciones.repository.CandidateRepository;
import co.edu.elecciones.repository.ElectionRepository;
import co.edu.elecciones.repository.ElectionResultSummaryRepository;
import co.edu.elecciones.repository.OfficialResultRepository;
import co.edu.elecciones.service.ResultManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResultManagementServiceTest {

    @Mock OfficialResultRepository results;
    @Mock ElectionResultSummaryRepository summaries;
    @Mock ElectionRepository elections;
    @Mock CandidateRepository candidates;

    private ResultManagementService service;
    private Election election;
    private Candidate candidate;

    @BeforeEach
    void setUp() {
        service = new ResultManagementService(results, summaries, elections, candidates);

        election = new Election();
        election.id = 1L;
        election.name = "Presidencia Colombia 2026";
        election.type = ElectionType.PRESIDENCIA;
        election.round = ElectionRound.PRIMERA;
        election.state = ElectionState.EN_CONTEO;
        election.electionDate = LocalDate.of(2026, 5, 31);

        Party party = new Party();
        party.id = 2L;
        party.name = "Partido Azul";
        party.acronym = "PA";
        party.color = "#2563eb";

        candidate = new Candidate();
        candidate.id = 3L;
        candidate.name = "María Fernández";
        candidate.active = true;
        candidate.party = party;
        candidate.election = election;
        candidate.electionType = ElectionType.PRESIDENCIA;
    }

    @Test
    void managementUsesPersistedSummaryAndServerAggregates() {
        OfficialResult entity = result(9L, 140_000L);
        when(elections.selectAll()).thenReturn(List.of(election));
        when(results.selectPage(anyLong(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));

        OfficialResultRepository.ManagementAggregate aggregate =
                mock(OfficialResultRepository.ManagementAggregate.class);
        when(aggregate.getRecords()).thenReturn(1L);
        when(aggregate.getCandidateVotes()).thenReturn(640_000L);
        when(aggregate.getValidated()).thenReturn(1L);
        when(aggregate.getPending()).thenReturn(0L);
        when(aggregate.getRejected()).thenReturn(0L);
        when(aggregate.getLastImportedAt()).thenReturn(Instant.parse("2026-07-02T20:00:00Z"));
        when(results.selectManagementAggregate(anyLong(), any(), any(), any())).thenReturn(aggregate);

        ElectionResultSummary summary = new ElectionResultSummary();
        summary.id = 4L;
        summary.election = election;
        summary.eligibleVoters = 1_000_000L;
        summary.totalVoters = 680_000L;
        summary.validVotes = 650_000L;
        summary.blankVotes = 10_000L;
        summary.nullVotes = 15_000L;
        summary.unmarkedVotes = 15_000L;
        summary.reportedTables = 8_200;
        summary.totalTables = 10_000;
        when(summaries.selectByElectionId(1L)).thenReturn(Optional.of(summary));
        when(results.selectTerritoryTables(1L)).thenReturn(List.of());
        when(results.selectDepartments(1L)).thenReturn(List.of("Antioquia"));
        when(results.selectMunicipalities(1L, "")).thenReturn(List.of("Medellín"));
        when(candidates.selectByElectionId(1L)).thenReturn(List.of(candidate));

        var management = service.management(null, null, "", "", "", 0, 10);

        assertEquals(1L, management.selectedElectionId());
        assertEquals(82.0, management.counters().tablePercentage());
        assertEquals(68.0, management.counters().participation());
        assertTrue(management.counters().reconciled());
        assertEquals("COMPLETA", management.counters().traceabilityStatus());
        assertEquals("María Fernández", management.results().items().get(0).candidate().name());
    }

    @Test
    void createCalculatesPercentageOnBackendAndPersistsValidationTrace() {
        AtomicReference<OfficialResult> saved = new AtomicReference<>();
        when(elections.selectById(1L)).thenReturn(Optional.of(election));
        when(candidates.selectById(3L)).thenReturn(Optional.of(candidate));
        when(results.selectByScope(1L, "Antioquia", "Medellín"))
                .thenAnswer(invocation -> saved.get() == null ? List.of() : List.of(saved.get()));
        when(results.selectByNaturalKey(1L, 3L, "Antioquia", "Medellín"))
                .thenReturn(Optional.empty());
        when(results.save(any(OfficialResult.class))).thenAnswer(invocation -> {
            OfficialResult entity = invocation.getArgument(0);
            entity.id = 9L;
            saved.set(entity);
            return entity;
        });
        when(results.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(results.selectById(9L)).thenAnswer(invocation -> Optional.of(saved.get()));

        var response = service.create(request());

        assertEquals(100.0, response.percentage());
        assertEquals("VALIDADO", response.validationStatus());
        assertEquals("Validaciones de integridad superadas", response.validationMessage());
        verify(results).saveAll(any());
    }

    @Test
    void rejectsCandidateFromAnotherElection() {
        Election other = new Election();
        other.id = 99L;
        candidate.election = other;
        when(elections.selectById(1L)).thenReturn(Optional.of(election));
        when(candidates.selectById(3L)).thenReturn(Optional.of(candidate));

        var exception = assertThrows(IllegalArgumentException.class, () -> service.create(request()));

        assertEquals("El candidato no pertenece a la elección seleccionada", exception.getMessage());
        verify(results, never()).save(any(OfficialResult.class));
    }

    @Test
    void rejectsInconsistentTerritoryMetadata() {
        OfficialResult existing = result(7L, 100L);
        existing.reportedTables = 7;
        existing.totalTables = 10;
        when(elections.selectById(1L)).thenReturn(Optional.of(election));
        when(candidates.selectById(3L)).thenReturn(Optional.of(candidate));
        when(results.selectByScope(1L, "Antioquia", "Medellín")).thenReturn(List.of(existing));

        var exception = assertThrows(BusinessConflictException.class, () -> service.create(request()));

        assertTrue(exception.getMessage().contains("deben coincidir"));
        verify(results, never()).save(any(OfficialResult.class));
    }


    @Test
    void validationRejectsEveryResultWhenTerritoryMetadataIsInconsistent() {
        OfficialResult first = result(7L, 100L);
        OfficialResult second = result(8L, 50L);
        second.reportedTables = 7;
        when(elections.selectById(1L)).thenReturn(Optional.of(election));
        when(results.selectByElectionId(1L)).thenReturn(List.of(first, second));
        when(results.selectByScope(1L, "Antioquia", "Medellín")).thenReturn(List.of(first, second));
        when(results.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.validateElection(1L);

        assertEquals(0, response.validated());
        assertEquals(2, response.rejected());
        assertEquals(ResultValidationStatus.RECHAZADO, first.validationStatus);
        assertEquals(0.0, first.percentage);
        verify(results, times(2)).saveAll(any());
    }

    @Test
    void summaryRejectsFutureCutoffBeforeQueryingDatabase() {
        ElectionResultSummaryRequest request = new ElectionResultSummaryRequest(
                1L, 100L, 80L, 70L, 5L, 3L, 2L, 8, 10,
                "CARGA_MANUAL", Instant.now().plusSeconds(3600)
        );

        var exception = assertThrows(IllegalArgumentException.class, () -> service.upsertSummary(request));

        assertEquals("La fecha de corte no puede estar en el futuro", exception.getMessage());
        verify(elections, never()).selectById(anyLong());
    }

    @Test
    void deleteRejectsArchivedElection() {
        election.state = ElectionState.ARCHIVADA;
        OfficialResult existing = result(7L, 100L);
        when(results.selectById(7L)).thenReturn(Optional.of(existing));

        var exception = assertThrows(IllegalArgumentException.class, () -> service.delete(7L));

        assertTrue(exception.getMessage().contains("archivada"));
        verify(results, never()).deleteByIdStatement(anyLong());
    }

    @Test
    void rejectsEmptyCsvWithoutPersistingAnything() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resultados.csv", "text/csv", new byte[0]
        );

        var exception = assertThrows(IllegalArgumentException.class, () -> service.importCsv(file));

        assertEquals("Selecciona un archivo CSV con contenido", exception.getMessage());
        verify(results, never()).save(any(OfficialResult.class));
    }

    private OfficialResultRequest request() {
        return new OfficialResultRequest(
                1L,
                3L,
                "Antioquia",
                "Medellín",
                140_000L,
                8,
                10,
                66.1,
                "CARGA_MANUAL"
        );
    }

    private OfficialResult result(Long id, Long votes) {
        OfficialResult result = new OfficialResult();
        result.id = id;
        result.election = election;
        result.candidate = candidate;
        result.department = "Antioquia";
        result.municipality = "Medellín";
        result.votes = votes;
        result.percentage = 100.0;
        result.reportedTables = 8;
        result.totalTables = 10;
        result.participation = 66.1;
        result.source = "CARGA_MANUAL";
        result.importedAt = Instant.parse("2026-07-02T20:00:00Z");
        result.validationStatus = ResultValidationStatus.VALIDADO;
        return result;
    }
}
