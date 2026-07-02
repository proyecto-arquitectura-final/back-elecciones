package co.edu.elecciones;

import co.edu.elecciones.domain.Candidate;
import co.edu.elecciones.domain.Party;
import co.edu.elecciones.domain.Poll;
import co.edu.elecciones.domain.PollResult;
import co.edu.elecciones.dto.Requests.PollRequest;
import co.edu.elecciones.dto.Requests.PollResultRequest;
import co.edu.elecciones.repository.CandidateRepository;
import co.edu.elecciones.repository.PollRepository;
import co.edu.elecciones.repository.PollResultRepository;
import co.edu.elecciones.service.PollService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PollServiceTest {

    @Mock
    private PollRepository polls;

    @Mock
    private PollResultRepository pollResults;

    @Mock
    private CandidateRepository candidates;

    private PollService service;

    @BeforeEach
    void setUp() {
        service = new PollService(polls, pollResults, candidates);
    }

    @Test
    void updateReplacesResultsWithoutOneToManyCollection() {
        Poll existing = new Poll();
        existing.id = 10L;
        existing.source = "Anterior";

        Party party = new Party();
        party.id = 2L;
        party.name = "Partido";

        Candidate candidate = new Candidate();
        candidate.id = 3L;
        candidate.name = "Candidata";
        candidate.party = party;

        when(polls.selectById(10L)).thenReturn(Optional.of(existing));
        when(polls.save(any(Poll.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(candidates.selectById(3L)).thenReturn(Optional.of(candidate));

        AtomicLong resultId = new AtomicLong(20);
        when(pollResults.save(any(PollResult.class))).thenAnswer(invocation -> {
            PollResult result = invocation.getArgument(0);
            result.id = resultId.getAndIncrement();
            return result;
        });

        PollRequest request = new PollRequest(
                "Nueva firma",
                LocalDate.of(2026, 4, 20),
                2_000,
                2.1,
                "Mixta",
                List.of(new PollResultRequest(3L, 41.2))
        );

        var response = service.update(10L, request);

        verify(pollResults).deleteByPollIdStatement(10L);
        assertEquals("Nueva firma", response.source());
        assertEquals(1, response.results().size());
        assertEquals("Candidata", response.results().get(0).candidate().name);
    }
}
