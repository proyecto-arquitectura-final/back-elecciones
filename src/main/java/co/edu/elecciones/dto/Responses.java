package co.edu.elecciones.dto;

import co.edu.elecciones.domain.Candidate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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

    public record PublicElection(Long id, String name, String type, String round, LocalDate date, String state) {
    }

    public record PublicSummary(
            long candidateVotes,
            long voters,
            long eligibleVoters,
            long validVotes,
            long blankVotes,
            long nullVotes,
            long unmarkedVotes,
            int reportedTables,
            int totalTables,
            double percentageTables,
            double participation,
            int departments,
            int municipalities,
            int resultRecords,
            long consistencyDifference,
            boolean consistent,
            String source,
            Instant lastUpdated
    ) {
    }

    public record PublicCandidate(
            int rank,
            Long id,
            String candidate,
            String party,
            String acronym,
            String color,
            long votes,
            double percentage,
            long gapVotes,
            double gapPercentage
    ) {
    }

    public record PublicTerritory(String level, String department, String municipality, int reportedTables,
                                  int totalTables, double processedPercentage, double participation,
                                  String leader, long votes) {
    }

    public record PublicDashboard(PublicElection election, List<PublicElection> elections,
                                  PublicSummary summary, List<PublicCandidate> candidates,
                                  List<PublicTerritory> territories) {
    }


    public record PublicPredictionMetrics(
            double processedPercentage,
            double confidence,
            double averageUncertainty,
            int pollCount,
            long totalSample,
            String modelMode,
            String dataQuality,
            double officialWeight,
            double pollWeight
    ) {
    }

    public record PublicPredictionCandidate(
            int rank,
            Long id,
            String candidate,
            String party,
            String acronym,
            String color,
            long votes,
            double currentPercentage,
            double pollAverage,
            double projectedPercentage,
            double probability,
            double uncertaintyMargin,
            double trend,
            int pollObservations
    ) {
    }

    public record PublicPollEvidence(
            Long id,
            String source,
            LocalDate date,
            int sampleSize,
            double marginError,
            String methodology
    ) {
    }

    public record PredictionFactor(
            String code,
            String title,
            String value,
            String description,
            String quality
    ) {
    }

    public record PublicPredictionDashboard(
            PublicElection election,
            List<PublicElection> elections,
            PublicPredictionMetrics metrics,
            List<PublicPredictionCandidate> candidates,
            List<PublicPollEvidence> polls,
            List<PredictionFactor> factors,
            Instant generatedAt
    ) {
    }

    public record ChatResponse(
            String answer,
            List<String> toolsUsed,
            UUID sessionId,
            Long messageId,
            String provider,
            String model,
            boolean fallback,
            List<String> sources,
            String disclaimer,
            Instant generatedAt
    ) {
    }

    public record ChatStatus(
            boolean enabled,
            String provider,
            String model,
            boolean persistenceEnabled,
            int historyLimit,
            String message
    ) {
    }

    public record ChatHistoryMessage(
            Long id,
            String role,
            String content,
            String provider,
            String model,
            boolean fallback,
            Boolean helpful,
            Instant createdAt
    ) {
    }

    public record ChatHistory(
            UUID sessionId,
            Long electionId,
            List<ChatHistoryMessage> messages
    ) {
    }

    public record ChatFeedbackResponse(Long messageId, boolean saved) {
    }

    public record Tool(String name, String description, List<String> allowedRoles) {
    }
}
