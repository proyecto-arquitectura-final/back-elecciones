package co.edu.elecciones.dto;

import co.edu.elecciones.domain.Candidate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public class Responses {
    public record PredictionItem(String candidate, String party, double currentPercentage, double projectedPercentage,
                                 double probability, double uncertaintyMargin) {
    }

    public record LiveSummary(long votes, double percentageTables, double participation, List<PredictionItem> leaders) {
    }

    public record DashboardAdmin(long activeElections, long users, long parties, long candidates, long auditEvents) {
    }

    public record PollResultResponse(Long id, Instant createdAt, Instant updatedAt, Candidate candidate,
                                     Double percentage) {
    }

    public record PollResponse(Long id, Instant createdAt, Instant updatedAt, String source, LocalDate date,
                               Integer sampleSize, Double marginError, String methodology,
                               List<PollResultResponse> results) {
    }

    public record ChatResponse(String answer, List<String> toolsUsed) {
    }

    public record Tool(String name, String description, List<String> allowedRoles) {
    }
}
