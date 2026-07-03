package co.edu.elecciones;

import co.edu.elecciones.config.BusinessConflictException;
import co.edu.elecciones.domain.Candidate;
import co.edu.elecciones.domain.Election;
import co.edu.elecciones.domain.ElectionRound;
import co.edu.elecciones.domain.ElectionState;
import co.edu.elecciones.domain.ElectionType;
import co.edu.elecciones.domain.Party;
import co.edu.elecciones.domain.Poll;
import co.edu.elecciones.domain.PollResult;
import co.edu.elecciones.domain.PollStatus;
import co.edu.elecciones.dto.Requests.PollRequest;
import co.edu.elecciones.dto.Requests.PollResultRequest;
import co.edu.elecciones.repository.CandidateRepository;
import co.edu.elecciones.repository.ElectionRepository;
import co.edu.elecciones.repository.PollRepository;
import co.edu.elecciones.repository.PollResultRepository;
import co.edu.elecciones.service.PollService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PollServiceTest {

    @Mock private PollRepository polls;
    @Mock private PollResultRepository pollResults;
    @Mock private CandidateRepository candidates;
    @Mock private ElectionRepository elections;

    private PollService service;
    private Election election;
    private Candidate candidate;

    @BeforeEach
    void setUp() {
        service = new PollService(polls, pollResults, candidates, elections);

        election = new Election();
        election.id = 1L;
        election.name = "Presidencia 2026";
        election.type = ElectionType.PRESIDENCIA;
        election.round = ElectionRound.PRIMERA;
        election.state = ElectionState.CONFIGURADA;
        election.electionDate = LocalDate.of(2026, 5, 31);

        Party party = new Party();
        party.id = 2L;
        party.name = "Partido";
        party.acronym = "P";
        party.active = true;

        candidate = new Candidate();
        candidate.id = 3L;
        candidate.name = "Candidata";
        candidate.party = party;
        candidate.election = election;
        candidate.active = true;
    }

    @Test
    void updateReplacesResultsWithoutOneToManyCollection() {
        Poll existing = poll(10L, "Anterior");
        when(polls.selectById(10L)).thenReturn(Optional.of(existing));
        when(elections.selectById(1L)).thenReturn(Optional.of(election));
        when(polls.selectDuplicateCount(1L, "Nueva firma", LocalDate.of(2026, 4, 20), 10L)).thenReturn(0L);
        when(polls.save(any(Poll.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(candidates.selectByIds(any())).thenReturn(List.of(candidate));

        AtomicLong resultId = new AtomicLong(20);
        when(pollResults.saveAll(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<PollResult> results = (List<PollResult>) invocation.getArgument(0);
            results.forEach(result -> result.id = resultId.getAndIncrement());
            return results;
        });

        PollRequest request = request(
                "Nueva firma",
                PollStatus.APROBADA,
                List.of(new PollResultRequest(3L, 41.2))
        );

        var response = service.update(10L, request);

        verify(pollResults).deleteByPollIdStatement(10L);
        assertEquals("Nueva firma", response.source());
        assertEquals("APROBADA", response.status());
        assertEquals(1, response.results().size());
        assertEquals("Candidata", response.results().get(0).candidate().name());
    }

    @Test
    void rejectsDuplicateCandidatesBeforePersisting() {
        when(elections.selectById(1L)).thenReturn(Optional.of(election));
        when(polls.selectDuplicateCount(any(), any(), any(), any())).thenReturn(0L);

        PollRequest request = request(
                "Firma",
                PollStatus.PENDIENTE,
                List.of(new PollResultRequest(3L, 30.0), new PollResultRequest(3L, 20.0))
        );

        assertThrows(BusinessConflictException.class, () -> service.create(request));
        verify(polls, never()).save(any(Poll.class));
    }

    @Test
    void rejectsCandidateFromAnotherElection() {
        Election other = new Election();
        other.id = 99L;
        candidate.election = other;
        when(elections.selectById(1L)).thenReturn(Optional.of(election));
        when(polls.selectDuplicateCount(any(), any(), any(), any())).thenReturn(0L);
        when(candidates.selectByIds(any())).thenReturn(List.of(candidate));

        var exception = assertThrows(IllegalArgumentException.class, () -> service.create(
                request("Firma", PollStatus.PENDIENTE, List.of(new PollResultRequest(3L, 30.0)))
        ));

        assertEquals("Todos los candidatos deben pertenecer a la elección seleccionada", exception.getMessage());
    }

    @Test
    void rejectsAccumulatedPercentagesAboveOneHundred() {
        Candidate second = new Candidate();
        second.id = 4L;
        second.name = "Segundo";
        second.party = candidate.party;
        second.election = election;
        second.active = true;

        when(elections.selectById(1L)).thenReturn(Optional.of(election));
        when(polls.selectDuplicateCount(any(), any(), any(), any())).thenReturn(0L);
        when(candidates.selectByIds(any())).thenReturn(List.of(candidate, second));

        var exception = assertThrows(IllegalArgumentException.class, () -> service.create(
                request(
                        "Firma",
                        PollStatus.PENDIENTE,
                        List.of(new PollResultRequest(3L, 60.0), new PollResultRequest(4L, 50.0))
                )
        ));

        assertEquals("La suma de porcentajes no puede superar 100%. Total recibido: 110.0%", exception.getMessage());
    }

    @Test
    void rejectsFutureDateEvenWhenCalledOutsideControllerValidation() {
        PollRequest request = new PollRequest(
                1L,
                "Firma",
                LocalDate.now().plusDays(1),
                1_000,
                2.5,
                "Telefónica",
                PollStatus.PENDIENTE,
                List.of(new PollResultRequest(3L, 30.0))
        );

        var exception = assertThrows(IllegalArgumentException.class, () -> service.create(request));

        assertEquals("La fecha de la encuesta no puede estar en el futuro", exception.getMessage());
        verify(polls, never()).save(any(Poll.class));
    }

    @Test
    void importsGroupedCsvRowsAsOnePollWithTwoResults() {
        String csv = """
                electionId,source,date,sampleSize,marginError,methodology,status,candidateId,percentage
                1,Firma CSV,2026-04-20,2000,2.1,Mixta,APROBADA,3,55.5
                1,Firma CSV,2026-04-20,2000,2.1,Mixta,APROBADA,4,44.5
                """;
        Candidate second = new Candidate();
        second.id = 4L;
        second.name = "Segundo";
        second.party = candidate.party;
        second.election = election;
        second.active = true;

        when(elections.selectById(1L)).thenReturn(Optional.of(election));
        when(polls.selectDuplicateCount(any(), any(), any(), any())).thenReturn(0L);
        when(candidates.selectByIds(any())).thenReturn(List.of(candidate, second));
        when(polls.save(any(Poll.class))).thenAnswer(invocation -> {
            Poll saved = invocation.getArgument(0);
            saved.id = 50L;
            return saved;
        });
        when(pollResults.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = new MockMultipartFile(
                "file", "encuestas.csv", "text/csv", csv.getBytes()
        );

        var imported = service.importCsv(file);

        assertEquals(1, imported.polls());
        assertEquals(2, imported.results());
    }

    @Test
    void rejectsEmptyCsv() {
        MockMultipartFile file = new MockMultipartFile("file", "encuestas.csv", "text/csv", new byte[0]);

        var exception = assertThrows(IllegalArgumentException.class, () -> service.importCsv(file));

        assertEquals("Selecciona un archivo CSV con contenido", exception.getMessage());
    }

    private PollRequest request(String source, PollStatus status, List<PollResultRequest> results) {
        return new PollRequest(
                1L,
                source,
                LocalDate.of(2026, 4, 20),
                2_000,
                2.1,
                "Mixta",
                status,
                results
        );
    }

    private Poll poll(Long id, String source) {
        Poll poll = new Poll();
        poll.id = id;
        poll.election = election;
        poll.source = source;
        poll.date = LocalDate.of(2026, 4, 10);
        poll.sampleSize = 1_500;
        poll.marginError = 2.5;
        poll.methodology = "Telefónica";
        poll.status = PollStatus.PENDIENTE;
        return poll;
    }
}
