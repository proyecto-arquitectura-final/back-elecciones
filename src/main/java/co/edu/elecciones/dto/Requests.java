package co.edu.elecciones.dto;

import co.edu.elecciones.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

public class Requests {
    public record UserRequest(
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Email @Size(max = 255) String email,
            @Size(min = 8, max = 128) String password,
            @NotNull Role role,
            @NotNull Boolean active
    ) {
    }

    public record PartyRequest(String name, String acronym, String color, Integer foundationYear, Boolean active) {
    }

    public record CandidateRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 160) String vicePresidentName,
            @NotNull Long partyId,
            @NotNull Long electionId,
            ElectionType electionType,
            @Size(max = 120) String department,
            @Size(max = 120) String municipality,
            Boolean active
    ) {
    }

    public record ElectionRequest(
            @NotBlank @Size(max = 180) String name,
            @NotNull ElectionType type,
            @NotNull ElectionRound round,
            @NotNull LocalDate electionDate,
            @NotNull ElectionState state
    ) {
    }

    public record PollResultRequest(
            @NotNull @Positive Long candidateId,
            @NotNull @DecimalMin("0.0") @DecimalMax("100.0") Double percentage
    ) {
    }

    public record PollRequest(
            @NotNull Long electionId,
            @NotBlank @Size(max = 160) String source,
            @NotNull @PastOrPresent LocalDate date,
            @NotNull @Min(1) @Max(10_000_000) Integer sampleSize,
            @NotNull @DecimalMin(value = "0.0", inclusive = false) @DecimalMax("20.0") Double marginError,
            @NotBlank @Size(max = 500) String methodology,
            @NotNull PollStatus status,
            @NotEmpty @Size(max = 100) List<@Valid PollResultRequest> results
    ) {
    }

    public record OfficialResultRequest(
            @NotNull @Positive Long electionId,
            @NotNull @Positive Long candidateId,
            @Size(max = 120) String department,
            @Size(max = 120) String municipality,
            @NotNull @Min(0) Long votes,
            @NotNull @Min(0) @Max(1_000_000) Integer reportedTables,
            @NotNull @Min(0) @Max(1_000_000) Integer totalTables,
            @NotNull @DecimalMin("0.0") @DecimalMax("100.0") Double participation,
            @Size(max = 160) String source
    ) {
    }

    public record ElectionResultSummaryRequest(
            @NotNull @Positive Long electionId,
            @NotNull @Min(0) Long eligibleVoters,
            @NotNull @Min(0) Long totalVoters,
            @NotNull @Min(0) Long validVotes,
            @NotNull @Min(0) Long blankVotes,
            @NotNull @Min(0) Long nullVotes,
            @NotNull @Min(0) Long unmarkedVotes,
            @NotNull @Min(0) @Max(1_000_000) Integer reportedTables,
            @NotNull @Min(0) @Max(1_000_000) Integer totalTables,
            @Size(max = 160) String source,
            @PastOrPresent Instant importedAt
    ) {
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
