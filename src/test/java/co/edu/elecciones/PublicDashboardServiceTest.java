package co.edu.elecciones;

import co.edu.elecciones.domain.Candidate;
import co.edu.elecciones.domain.Election;
import co.edu.elecciones.domain.ElectionResultSummary;
import co.edu.elecciones.domain.ElectionRound;
import co.edu.elecciones.domain.ElectionState;
import co.edu.elecciones.domain.ElectionType;
import co.edu.elecciones.domain.OfficialResult;
import co.edu.elecciones.domain.Party;
import co.edu.elecciones.repository.ElectionRepository;
import co.edu.elecciones.repository.ElectionResultSummaryRepository;
import co.edu.elecciones.repository.OfficialResultRepository;
import co.edu.elecciones.service.PublicDashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicDashboardServiceTest {

    @Mock
    private ElectionRepository elections;
    @Mock
    private OfficialResultRepository officialResults;
    @Mock
    private ElectionResultSummaryRepository summaries;

    private PublicDashboardService service;

    @BeforeEach
    void setUp() {
        service = new PublicDashboardService(elections, officialResults, summaries);
    }

    @Test
    void resultsScreenUsesPersistedSummaryAndCandidateRows() {
        Election election = election();
        Party partyA = party(1L, "Partido A", "PA", "#2563eb");
        Party partyB = party(2L, "Partido B", "PB", "#dc2626");
        Candidate candidateA = candidate(10L, "Candidata A", partyA);
        Candidate candidateB = candidate(20L, "Candidato B", partyB);

        OfficialResult resultA = officialResult(100L, election, candidateA, 600L);
        OfficialResult resultB = officialResult(200L, election, candidateB, 400L);
        ElectionResultSummary summary = summary(election);

        when(elections.selectAll()).thenReturn(List.of(election));
        when(officialResults.selectByElectionId(1L)).thenReturn(List.of(resultA, resultB));
        when(summaries.selectByElectionId(1L)).thenReturn(Optional.of(summary));

        var dashboard = service.load(null);

        assertNotNull(dashboard.election());
        assertEquals(1L, dashboard.election().id());
        assertEquals(1_000L, dashboard.summary().candidateVotes());
        assertEquals(1_100L, dashboard.summary().voters());
        assertEquals(1_050L, dashboard.summary().validVotes());
        assertEquals(50L, dashboard.summary().blankVotes());
        assertEquals(5, dashboard.summary().reportedTables());
        assertEquals(10, dashboard.summary().totalTables());
        assertEquals(50.0, dashboard.summary().percentageTables());
        assertEquals(55.0, dashboard.summary().participation());
        assertTrue(dashboard.summary().consistent());
        assertEquals(0, dashboard.summary().consistencyDifference());

        assertEquals(2, dashboard.candidates().size());
        assertEquals(1, dashboard.candidates().get(0).rank());
        assertEquals("Candidata A", dashboard.candidates().get(0).candidate());
        assertEquals(57.1, dashboard.candidates().get(0).percentage());
        assertEquals(0L, dashboard.candidates().get(0).gapVotes());
        assertEquals(200L, dashboard.candidates().get(1).gapVotes());

        // Departamento + municipio.
        assertEquals(2, dashboard.territories().size());
    }

    @Test
    void inconsistentSummaryIsExposedAsDataQualityWarning() {
        Election election = election();
        Party party = party(1L, "Partido A", "PA", "#2563eb");
        Candidate candidate = candidate(10L, "Candidata A", party);
        OfficialResult result = officialResult(100L, election, candidate, 600L);
        ElectionResultSummary summary = summary(election);
        summary.validVotes = 900L;
        summary.blankVotes = 100L;

        when(elections.selectAll()).thenReturn(List.of(election));
        when(officialResults.selectByElectionId(1L)).thenReturn(List.of(result));
        when(summaries.selectByElectionId(1L)).thenReturn(Optional.of(summary));

        var dashboard = service.load(1L);

        assertFalse(dashboard.summary().consistent());
        assertEquals(200L, dashboard.summary().consistencyDifference());
    }

    @Test
    void csvExportContainsCandidatesAndTerritories() {
        Election election = election();
        Party party = party(1L, "Partido A", "PA", "#2563eb");
        Candidate candidate = candidate(10L, "Candidata A", party);
        OfficialResult result = officialResult(100L, election, candidate, 600L);

        when(elections.selectAll()).thenReturn(List.of(election));
        when(officialResults.selectByElectionId(1L)).thenReturn(List.of(result));
        when(summaries.selectByElectionId(1L)).thenReturn(Optional.empty());

        String csv = new String(service.exportCsv(1L), java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(csv.contains("CANDIDATO"));
        assertTrue(csv.contains("Candidata A"));
        assertTrue(csv.contains("DEPARTAMENTO"));
    }

    private Election election() {
        Election election = new Election();
        election.id = 1L;
        election.name = "Elección persistida";
        election.type = ElectionType.PRESIDENCIA;
        election.round = ElectionRound.PRIMERA;
        election.state = ElectionState.EN_CONTEO;
        election.electionDate = LocalDate.of(2026, 5, 31);
        return election;
    }

    private Party party(Long id, String name, String acronym, String color) {
        Party party = new Party();
        party.id = id;
        party.name = name;
        party.acronym = acronym;
        party.color = color;
        return party;
    }

    private Candidate candidate(Long id, String name, Party party) {
        Candidate candidate = new Candidate();
        candidate.id = id;
        candidate.name = name;
        candidate.party = party;
        candidate.electionType = ElectionType.PRESIDENCIA;
        return candidate;
    }

    private OfficialResult officialResult(Long id, Election election, Candidate candidate, Long votes) {
        OfficialResult result = new OfficialResult();
        result.id = id;
        result.election = election;
        result.candidate = candidate;
        result.department = "Antioquia";
        result.municipality = "Medellín";
        result.votes = votes;
        result.reportedTables = 5;
        result.totalTables = 10;
        result.participation = 55.0;
        result.source = "FUENTE_PRUEBA";
        result.importedAt = Instant.parse("2026-05-31T12:00:00Z");
        return result;
    }

    private ElectionResultSummary summary(Election election) {
        ElectionResultSummary summary = new ElectionResultSummary();
        summary.id = 50L;
        summary.election = election;
        summary.eligibleVoters = 2_000L;
        summary.totalVoters = 1_100L;
        summary.validVotes = 1_050L;
        summary.blankVotes = 50L;
        summary.nullVotes = 30L;
        summary.unmarkedVotes = 20L;
        summary.reportedTables = 5;
        summary.totalTables = 10;
        summary.source = "FUENTE_OFICIAL";
        summary.importedAt = Instant.parse("2026-05-31T13:00:00Z");
        return summary;
    }
}
