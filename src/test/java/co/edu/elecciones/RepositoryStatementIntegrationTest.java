package co.edu.elecciones;

import co.edu.elecciones.domain.Candidate;
import co.edu.elecciones.domain.Election;
import co.edu.elecciones.domain.ElectionResultSummary;
import co.edu.elecciones.domain.ElectionRound;
import co.edu.elecciones.domain.ElectionState;
import co.edu.elecciones.domain.ElectionType;
import co.edu.elecciones.domain.Party;
import co.edu.elecciones.domain.Poll;
import co.edu.elecciones.domain.PollResult;
import co.edu.elecciones.repository.CandidateRepository;
import co.edu.elecciones.repository.ElectionRepository;
import co.edu.elecciones.repository.ElectionResultSummaryRepository;
import co.edu.elecciones.repository.PartyRepository;
import co.edu.elecciones.repository.PollRepository;
import co.edu.elecciones.repository.PollResultRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
class RepositoryStatementIntegrationTest {

    @Autowired
    private PartyRepository parties;

    @Autowired
    private ElectionRepository elections;

    @Autowired
    private ElectionResultSummaryRepository summaries;

    @Autowired
    private CandidateRepository candidates;

    @Autowired
    private PollRepository polls;

    @Autowired
    private PollResultRepository pollResults;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void explicitQueriesLoadPollResultsAndManyToOneRelations() {
        Party party = new Party();
        party.name = "Partido de prueba";
        party.acronym = "PP";
        party = parties.save(party);

        Candidate candidate = new Candidate();
        candidate.name = "Candidata de prueba";
        candidate.party = party;
        candidate.electionType = ElectionType.PRESIDENCIA;
        candidate = candidates.save(candidate);

        Poll poll = new Poll();
        poll.source = "Firma de prueba";
        poll.date = LocalDate.of(2026, 4, 10);
        poll.sampleSize = 1_500;
        poll.marginError = 2.5;
        poll.methodology = "Telefónica";
        poll = polls.save(poll);

        PollResult result = new PollResult();
        result.poll = poll;
        result.candidate = candidate;
        result.percentage = 42.5;
        pollResults.save(result);

        entityManager.flush();
        entityManager.clear();

        assertEquals(1, polls.selectCount());
        assertEquals("Firma de prueba", polls.selectById(poll.id).orElseThrow().source);

        List<PollResult> selected = pollResults.selectByPollId(poll.id);
        assertEquals(1, selected.size());
        assertEquals("Candidata de prueba", selected.get(0).candidate.name);
        assertEquals("Partido de prueba", selected.get(0).candidate.party.name);
        assertTrue(parties.selectAcronymCount("pp") > 0);
    }
    @Test
    void explicitQueryLoadsElectionResultSummary() {
        Election election = new Election();
        election.name = "Elección de resumen";
        election.type = ElectionType.PRESIDENCIA;
        election.round = ElectionRound.PRIMERA;
        election.state = ElectionState.EN_CONTEO;
        election = elections.save(election);

        ElectionResultSummary summary = new ElectionResultSummary();
        summary.election = election;
        summary.eligibleVoters = 1_000L;
        summary.totalVoters = 700L;
        summary.validVotes = 680L;
        summary.blankVotes = 20L;
        summary.nullVotes = 10L;
        summary.unmarkedVotes = 10L;
        summary.reportedTables = 7;
        summary.totalTables = 10;
        summaries.save(summary);

        entityManager.flush();
        entityManager.clear();

        ElectionResultSummary selected = summaries.selectByElectionId(election.id).orElseThrow();
        assertEquals(700L, selected.totalVoters);
        assertEquals("Elección de resumen", selected.election.name);
    }

}
