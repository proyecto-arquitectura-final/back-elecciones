package co.edu.elecciones;

import co.edu.elecciones.domain.Candidate;
import co.edu.elecciones.domain.Election;
import co.edu.elecciones.domain.ElectionRound;
import co.edu.elecciones.domain.ElectionState;
import co.edu.elecciones.domain.ElectionType;
import co.edu.elecciones.domain.OfficialResult;
import co.edu.elecciones.domain.Party;
import co.edu.elecciones.domain.ResultValidationStatus;
import co.edu.elecciones.repository.ElectionRepository;
import co.edu.elecciones.repository.OfficialResultRepository;
import co.edu.elecciones.repository.ReportGenerationRepository;
import co.edu.elecciones.service.AuditService;
import co.edu.elecciones.service.ReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {
    @Mock OfficialResultRepository results;
    @Mock ElectionRepository elections;
    @Mock ReportGenerationRepository generations;
    @Mock AuditService audit;
    private ReportService service;
    private Election election;

    @BeforeEach
    void setUp() {
        service = new ReportService(results, elections, generations, audit, new ObjectMapper());
        election = new Election();
        election.id = 1L;
        election.name = "Presidencia 2026";
        election.type = ElectionType.PRESIDENCIA;
        election.round = ElectionRound.PRIMERA;
        election.electionDate = LocalDate.of(2026, 5, 31);
        election.state = ElectionState.EN_CONTEO;
    }

    @Test
    void buildsRegionalSummaryFromBackendProjection() {
        when(elections.selectAll()).thenReturn(List.of(election));
        when(elections.selectById(1L)).thenReturn(Optional.of(election));
        OfficialResultRepository.ReportRegionRow row = mock(OfficialResultRepository.ReportRegionRow.class);
        when(row.getRegion()).thenReturn("Bogotá D.C.");
        when(row.getVotes()).thenReturn(300L);
        when(row.getParticipation()).thenReturn(65.5);
        when(row.getReportedTables()).thenReturn(8L);
        when(row.getTotalTables()).thenReturn(10L);
        when(results.selectReportRegions(1L)).thenReturn(List.of(row));
        when(results.selectValidatedByElectionId(1L)).thenReturn(List.of(result()));

        var response = service.management(1L);

        assertEquals(300L, response.counters().votes());
        assertEquals(80.0, response.counters().processedPercentage());
        assertEquals("Bogotá D.C.", response.regions().get(0).region());
    }

    @Test
    void generatesEscapedCsvAndPersistsTrace() {
        OfficialResult result = result();
        result.candidate.name = "Pérez, Ana";
        when(elections.selectAll()).thenReturn(List.of(election));
        when(elections.selectById(1L)).thenReturn(Optional.of(election));
        when(results.selectValidatedByElectionId(1L)).thenReturn(List.of(result));
        when(results.selectReportRegions(1L)).thenReturn(List.of());
        when(generations.save(any())).thenAnswer(invocation -> {
            var entity = invocation.getArgument(0, co.edu.elecciones.domain.ReportGeneration.class);
            entity.id = 9L;
            return entity;
        });

        var generated = service.generate(1L, "csv", null);
        String csv = new String(generated.content(), java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(csv.contains("\"Pérez, Ana\""));
        assertEquals("resultados-eleccion-1.csv", generated.filename());
    }

    private OfficialResult result() {
        Party party = new Party();
        party.id = 1L;
        party.name = "Partido";
        party.acronym = "PAR";
        Candidate candidate = new Candidate();
        candidate.id = 1L;
        candidate.name = "Ana Pérez";
        candidate.party = party;
        candidate.election = election;
        OfficialResult result = new OfficialResult();
        result.id = 1L;
        result.election = election;
        result.candidate = candidate;
        result.department = "Bogotá D.C.";
        result.municipality = "Bogotá";
        result.votes = 300L;
        result.percentage = 50.0;
        result.reportedTables = 8;
        result.totalTables = 10;
        result.participation = 65.5;
        result.source = "QA";
        result.validationStatus = ResultValidationStatus.VALIDADO;
        return result;
    }
}
