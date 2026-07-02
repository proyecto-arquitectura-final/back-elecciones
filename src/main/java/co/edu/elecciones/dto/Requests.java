package co.edu.elecciones.dto;

import co.edu.elecciones.domain.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

public class Requests {
    public record UserRequest(String name, String email, String password, Role role, Boolean active) {
    }

    public record PartyRequest(String name, String acronym, String color, Integer foundationYear, Boolean active) {
    }

    public record CandidateRequest(String name, String vicePresidentName, Long partyId, ElectionType electionType,
                                   String department, String municipality, Boolean active) {
    }

    public record ElectionRequest(String name, ElectionType type, ElectionRound round, LocalDate electionDate,
                                  ElectionState state) {
    }

    public record PollResultRequest(Long candidateId, Double percentage) {
    }

    public record PollRequest(String source, LocalDate date, Integer sampleSize, Double marginError, String methodology,
                              List<PollResultRequest> results) {
    }

    public record OfficialResultRequest(Long electionId, Long candidateId, String department, String municipality,
                                        Long votes, Double percentage, Integer reportedTables, Integer totalTables,
                                        Double participation, String source) {
    }

    public record ElectionResultSummaryRequest(Long electionId, Long eligibleVoters, Long totalVoters,
                                               Long validVotes, Long blankVotes, Long nullVotes,
                                               Long unmarkedVotes, Integer reportedTables, Integer totalTables,
                                               String source, Instant importedAt) {
    }

    public record ChatRequest(
            @NotBlank @Size(max = 600) String question,
            Long electionId,
            UUID sessionId
    ) {
    }

    public record ChatFeedbackRequest(
            @NotNull UUID sessionId,
            @NotNull Long messageId,
            @NotNull Boolean helpful,
            @Size(max = 500) String comment
    ) {
    }

    public record McpInvokeRequest(String tool, Map<String, Object> arguments) {
    }
}
