package co.edu.elecciones;

import co.edu.elecciones.domain.Candidate;
import co.edu.elecciones.domain.Election;
import co.edu.elecciones.domain.ElectionResultSummary;
import co.edu.elecciones.domain.ElectionRound;
import co.edu.elecciones.domain.ElectionState;
import co.edu.elecciones.domain.ElectionType;
import co.edu.elecciones.domain.OfficialResult;
import co.edu.elecciones.domain.Party;
import co.edu.elecciones.domain.Poll;
import co.edu.elecciones.domain.PollResult;
import co.edu.elecciones.domain.PollStatus;
import co.edu.elecciones.repository.CandidateRepository;
import co.edu.elecciones.repository.ElectionRepository;
import co.edu.elecciones.repository.ElectionResultSummaryRepository;
import co.edu.elecciones.repository.OfficialResultRepository;
import co.edu.elecciones.repository.PollResultRepository;
import co.edu.elecciones.service.PublicPredictionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicPredictionServiceTest {

    @Mock private ElectionRepository elections;
    @Mock private ElectionResultSummaryRepository summaries;
    @Mock private OfficialResultRepository officialResults;
    @Mock private PollResultRepository pollResults;
    @Mock private CandidateRepository candidates;

    private PublicPredictionService service;

    @BeforeEach
    void setUp() {
        service = new PublicPredictionService(elections, summaries, officialResults, pollResults, candidates);
    }

    @Test
    void combinesPersistedResultsAndPollsForSelectedElection() {
        Election election = election();
        Party partyA = party(1L, "Partido A", "PA", "#2563eb");
        Party partyB = party(2L, "Partido B", "PB", "#dc2626");
        Candidate candidateA = candidate(10L, "Candidata A", partyA, election);
        Candidate candidateB = candidate(20L, "Candidato B", partyB, election);

        ElectionResultSummary summary = new ElectionResultSummary();
        summary.election = election;
        summary.reportedTables = 70;
        summary.totalTables = 100;

        OfficialResult resultA = result(election, candidateA, 600L);
        OfficialResult resultB = result(election, candidateB, 400L);
        Poll poll = poll();
        PollResult pollA = pollResult(poll, candidateA, 55.0);
        PollResult pollB = pollResult(poll, candidateB, 45.0);

        when(elections.selectAll()).thenReturn(List.of(election));
        when(elections.selectById(1L)).thenReturn(Optional.of(election));
        when(summaries.selectByElectionId(1L)).thenReturn(Optional.of(summary));
        when(candidates.selectAll()).thenReturn(List.of(candidateA, candidateB));
        when(officialResults.selectByElectionId(1L)).thenReturn(List.of(resultA, resultB));
        when(pollResults.selectApprovedByElectionId(1L)).thenReturn(List.of(pollA, pollB));

        var dashboard = service.load(1L);

        assertEquals("RESULTADOS_Y_ENCUESTAS", dashboard.metrics().modelMode());
        assertEquals(70.0, dashboard.metrics().processedPercentage());
        assertEquals(1, dashboard.metrics().pollCount());
        assertEquals(2, dashboard.candidates().size());
        assertEquals("Candidata A", dashboard.candidates().get(0).candidate());
        assertTrue(dashboard.candidates().get(0).probability() > dashboard.candidates().get(1).probability());
        assertTrue(dashboard.metrics().confidence() > 0);
    }

    private Election election() {
        Election election = new Election();
        election.id = 1L;
        election.name = "Elección persistida";
        election.type = ElectionType.PRESIDENCIA;
        election.round = ElectionRound.PRIMERA;
        election.state = ElectionState.EN_CONTEO;
        election.electionDate = LocalDate.now().plusDays(20);
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

    private Candidate candidate(Long id, String name, Party party, Election election) {
        Candidate candidate = new Candidate();
        candidate.id = id;
        candidate.name = name;
        candidate.party = party;
        candidate.election = election;
        candidate.active = true;
        candidate.electionType = ElectionType.PRESIDENCIA;
        return candidate;
    }

    private OfficialResult result(Election election, Candidate candidate, long votes) {
        OfficialResult result = new OfficialResult();
        result.election = election;
        result.candidate = candidate;
        result.votes = votes;
        return result;
    }

    private Poll poll() {
        Poll poll = new Poll();
        poll.id = 50L;
        poll.election = election();
        poll.status = PollStatus.APROBADA;
        poll.source = "Encuestadora";
        poll.date = LocalDate.now().minusDays(3);
        poll.sampleSize = 2_000;
        poll.marginError = 2.2;
        poll.methodology = "Mixta";
        return poll;
    }

    private PollResult pollResult(Poll poll, Candidate candidate, double percentage) {
        PollResult result = new PollResult();
        result.poll = poll;
        result.candidate = candidate;
        result.percentage = percentage;
        return result;
    }
}
