package co.edu.elecciones;

import co.edu.elecciones.domain.Candidate;
import co.edu.elecciones.domain.Election;
import co.edu.elecciones.domain.ElectionRound;
import co.edu.elecciones.domain.ElectionState;
import co.edu.elecciones.domain.ElectionType;
import co.edu.elecciones.domain.OfficialResult;
import co.edu.elecciones.domain.Party;
import co.edu.elecciones.domain.ResultValidationStatus;
import co.edu.elecciones.repository.CandidateRepository;
import co.edu.elecciones.repository.ElectionRepository;
import co.edu.elecciones.repository.OfficialResultRepository;
import co.edu.elecciones.repository.PartyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
class OfficialResultRepositoryIntegrationTest {

    @Autowired PartyRepository parties;
    @Autowired ElectionRepository elections;
    @Autowired CandidateRepository candidates;
    @Autowired OfficialResultRepository results;
    @Autowired TestEntityManager entityManager;

    @Test
    void explicitManagementQueriesLoadRelationsAggregatesAndTerritoryData() {
        Party party = new Party();
        party.name = "Partido resultados";
        party.acronym = "PR";
        party = parties.save(party);

        Election election = new Election();
        election.name = "Elección resultados";
        election.type = ElectionType.PRESIDENCIA;
        election.round = ElectionRound.PRIMERA;
        election.state = ElectionState.EN_CONTEO;
        election.electionDate = LocalDate.of(2026, 5, 31);
        election = elections.save(election);

        Candidate first = candidate("Candidata Uno", party, election);
        Candidate second = candidate("Candidato Dos", party, election);
        Candidate pendingCandidate = candidate("Candidatura Pendiente", party, election);
        first = candidates.save(first);
        second = candidates.save(second);
        pendingCandidate = candidates.save(pendingCandidate);

        results.save(result(first, election, 300L));
        results.save(result(second, election, 200L));
        OfficialResult pending = result(pendingCandidate, election, 999L);
        pending.validationStatus = ResultValidationStatus.PENDIENTE;
        results.save(pending);
        entityManager.flush();
        entityManager.clear();

        var page = results.selectPage(
                election.id,
                ResultValidationStatus.VALIDADO,
                "Antioquia",
                "Medellín",
                "candidata",
                PageRequest.of(0, 10)
        );
        assertEquals(1, page.getTotalElements());
        assertEquals("Candidata Uno", page.getContent().get(0).candidate.name);

        var aggregate = results.selectManagementAggregate(
                election.id,
                ResultValidationStatus.VALIDADO,
                ResultValidationStatus.PENDIENTE,
                ResultValidationStatus.RECHAZADO
        );
        assertEquals(3L, aggregate.getRecords());
        assertEquals(500L, aggregate.getCandidateVotes());
        assertEquals(2L, aggregate.getValidated());
        assertEquals(1L, aggregate.getPending());

        var territories = results.selectTerritoryTables(election.id);
        assertEquals(1, territories.size());
        assertEquals(8, territories.get(0).getReportedTables());
        assertEquals(10, territories.get(0).getTotalTables());
        assertEquals(3, results.selectByScope(election.id, "Antioquia", "Medellín").size());
        assertTrue(results.selectDepartments(election.id).contains("Antioquia"));
    }

    private Candidate candidate(String name, Party party, Election election) {
        Candidate candidate = new Candidate();
        candidate.name = name;
        candidate.party = party;
        candidate.election = election;
        candidate.electionType = ElectionType.PRESIDENCIA;
        return candidate;
    }

    private OfficialResult result(Candidate candidate, Election election, long votes) {
        OfficialResult result = new OfficialResult();
        result.election = election;
        result.candidate = candidate;
        result.department = "Antioquia";
        result.municipality = "Medellín";
        result.votes = votes;
        result.percentage = 50.0;
        result.reportedTables = 8;
        result.totalTables = 10;
        result.participation = 65.0;
        result.validationStatus = ResultValidationStatus.VALIDADO;
        return result;
    }
}
