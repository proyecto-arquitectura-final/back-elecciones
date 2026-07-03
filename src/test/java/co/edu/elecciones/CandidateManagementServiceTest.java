package co.edu.elecciones;

import co.edu.elecciones.config.BusinessConflictException;
import co.edu.elecciones.domain.Candidate;
import co.edu.elecciones.domain.Election;
import co.edu.elecciones.domain.ElectionRound;
import co.edu.elecciones.domain.ElectionState;
import co.edu.elecciones.domain.ElectionType;
import co.edu.elecciones.domain.Party;
import co.edu.elecciones.dto.Requests.CandidateRequest;
import co.edu.elecciones.repository.CandidateRepository;
import co.edu.elecciones.repository.ElectionRepository;
import co.edu.elecciones.repository.PartyRepository;
import co.edu.elecciones.service.CandidateManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandidateManagementServiceTest {

    @Mock CandidateRepository candidates;
    @Mock PartyRepository parties;
    @Mock ElectionRepository elections;

    private CandidateManagementService service;
    private Party party;
    private Election election;

    @BeforeEach
    void setUp() {
        service = new CandidateManagementService(candidates, parties, elections);

        party = new Party();
        party.id = 1L;
        party.name = "Partido de prueba";
        party.acronym = "PP";
        party.active = true;

        election = new Election();
        election.id = 2L;
        election.name = "Presidencia de prueba";
        election.type = ElectionType.PRESIDENCIA;
        election.round = ElectionRound.PRIMERA;
        election.state = ElectionState.CONFIGURADA;
        election.electionDate = LocalDate.of(2026, 5, 31);
    }

    @Test
    void createsCandidateUsingElectionTypeAndNormalizesTerritory() {
        when(parties.selectById(1L)).thenReturn(Optional.of(party));
        when(elections.selectById(2L)).thenReturn(Optional.of(election));
        when(candidates.selectDuplicateNameCount(2L, "María Fernández", null)).thenReturn(0L);
        when(candidates.save(any(Candidate.class))).thenAnswer(invocation -> {
            Candidate saved = invocation.getArgument(0);
            saved.id = 9L;
            return saved;
        });
        when(candidates.selectOfficialResultCount(9L)).thenReturn(0L);
        when(candidates.selectPollResultCount(9L)).thenReturn(0L);

        var response = service.create(new CandidateRequest(
                "  María   Fernández ",
                " Carlos Rojas ",
                1L,
                2L,
                ElectionType.PRESIDENCIA,
                "Bogotá",
                "Bogotá",
                true
        ));

        assertEquals("María Fernández", response.name());
        assertEquals("Carlos Rojas", response.vicePresidentName());
        assertEquals(2L, response.election().id());
        assertNull(response.department());
        assertTrue(response.deletable());
    }

    @Test
    void rejectsDuplicateCandidateForSameElection() {
        when(parties.selectById(1L)).thenReturn(Optional.of(party));
        when(elections.selectById(2L)).thenReturn(Optional.of(election));
        when(candidates.selectDuplicateNameCount(2L, "María Fernández", null)).thenReturn(1L);

        assertThrows(BusinessConflictException.class, () -> service.create(new CandidateRequest(
                "María Fernández",
                "Carlos Rojas",
                1L,
                2L,
                ElectionType.PRESIDENCIA,
                null,
                null,
                true
        )));
    }

    @Test
    void preventsDeleteWhenCandidateHasElectoralInformation() {
        Candidate candidate = new Candidate();
        candidate.id = 3L;
        candidate.name = "María Fernández";
        candidate.party = party;
        candidate.election = election;
        candidate.electionType = ElectionType.PRESIDENCIA;

        when(candidates.selectById(3L)).thenReturn(Optional.of(candidate));
        when(candidates.selectOfficialResultCount(3L)).thenReturn(4L);
        when(candidates.selectPollResultCount(3L)).thenReturn(2L);

        assertThrows(BusinessConflictException.class, () -> service.delete(3L));
    }

    @Test
    void deletesCandidateWithoutDependencies() {
        Candidate candidate = new Candidate();
        candidate.id = 3L;
        candidate.name = "Sin datos";
        when(candidates.selectById(3L)).thenReturn(Optional.of(candidate));
        when(candidates.selectOfficialResultCount(3L)).thenReturn(0L);
        when(candidates.selectPollResultCount(3L)).thenReturn(0L);

        service.delete(3L);

        verify(candidates).deleteByIdStatement(3L);
    }
}
