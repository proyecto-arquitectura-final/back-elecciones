package co.edu.elecciones.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class Responses {

    public record CandidateParty(
            Long id,
            String name,
            String acronym,
            String color,
            boolean active
    ) {
    }

    public record CandidateElection(
            Long id,
            String name,
            String type,
            String round,
            LocalDate date,
            String state
    ) {
    }

    public record CandidateResponse(
            Long id,
            Instant createdAt,
            Instant updatedAt,
            String name,
            String vicePresidentName,
            CandidateParty party,
            CandidateElection election,
            String electionType,
            String department,
            String municipality,
            boolean active,
            long officialResultCount,
            long pollResultCount,
            boolean deletable
    ) {
    }

    public record CandidateCounters(
            long total,
            long active,
            long inactive,
            long presidency,
            long senate,
            long chamber,
            long representedParties
    ) {
    }

    public record CandidateManagement(
            CandidateCounters counters,
            List<CandidateResponse> candidates,
            List<CandidateParty> parties,
            List<CandidateElection> elections,
            Instant generatedAt
    ) {
    }
    public record ElectionResponse(
            Long id,
            Instant createdAt,
            Instant updatedAt,
            String name,
            String type,
            String round,
            LocalDate electionDate,
            String state
    ) {
    }

    public record ElectionManagementCounters(
            long total,
            long configured,
            long open,
            long counting,
            long closed,
            long archived,
            long withSummary,
            long withoutSummary
    ) {
    }

    public record ElectionManagementItem(
            Long id,
            Instant createdAt,
            Instant updatedAt,
            String name,
            String type,
            String round,
            LocalDate electionDate,
            String state,
            int reportedTables,
            int totalTables,
            Double progress,
            boolean summaryAvailable,
            long candidateCount,
            long officialResultCount,
            long assistantSessionCount,
            boolean structureLocked,
            boolean deletable,
            List<String> allowedStates
    ) {
    }

    public record ElectionManagement(
            ElectionManagementCounters counters,
            List<ElectionManagementItem> elections,
            Instant generatedAt
    ) {
    }

    public record PredictionItem(String candidate, String party, double currentPercentage, double projectedPercentage,
                                 double probability, double uncertaintyMargin) {
    }

    public record LiveSummary(long votes, double percentageTables, double participation, List<PredictionItem> leaders) {
    }

    public record ResultPartyResponse(Long id, String name, String acronym, String color) {
    }

    public record ResultElectionResponse(
            Long id,
            String name,
            String type,
            String round,
            LocalDate date,
            String state
    ) {
    }

    public record ResultCandidateResponse(
            Long id,
            String name,
            boolean active,
            Long electionId,
            ResultPartyResponse party
    ) {
    }

    public record OfficialResultResponse(
            Long id,
            Instant createdAt,
            Instant updatedAt,
            ResultElectionResponse election,
            ResultCandidateResponse candidate,
            String department,
            String municipality,
            long votes,
            double percentage,
            int reportedTables,
            int totalTables,
            double participation,
            String source,
            Instant importedAt,
            String validationStatus,
            String validationMessage,
            Instant validatedAt,
            String validatedBy
    ) {
    }

    public record ResultSummaryResponse(
            Long id,
            Long electionId,
            long eligibleVoters,
            long totalVoters,
            long validVotes,
            long blankVotes,
            long nullVotes,
            long unmarkedVotes,
            int reportedTables,
            int totalTables,
            double tablePercentage,
            double participation,
            String source,
            Instant importedAt
    ) {
    }

    public record ResultStatusOption(String value, String label) {
    }

    public record ResultManagementCounters(
            long records,
            long candidateVotes,
            int reportedTables,
            int totalTables,
            double tablePercentage,
            double participation,
            long validated,
            long pending,
            long rejected,
            String traceabilityStatus,
            long reconciliationDifference,
            boolean reconciled,
            Instant lastImportedAt
    ) {
    }

    public record OfficialResultPage(
            List<OfficialResultResponse> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }

    public record ResultManagement(
            Long selectedElectionId,
            ResultManagementCounters counters,
            ResultSummaryResponse summary,
            OfficialResultPage results,
            List<ResultElectionResponse> elections,
            List<ResultCandidateResponse> candidates,
            List<ResultStatusOption> validationStatuses,
            List<String> departments,
            List<String> municipalities,
            Instant generatedAt
    ) {
    }

    public record ResultImportResponse(int created, int updated, int processed) {
    }

    public record ResultValidationResponse(long validated, long rejected, long recalculatedScopes) {
    }

    public record DashboardCounters(
            long activeElections,
            long candidates,
            long polls,
            long users,
            long parties,
            long auditEvents,
            long resultRecords
    ) {
    }

    public record DashboardElection(
            Long id,
            String name,
            String type,
            LocalDate date,
            String state,
            int reportedTables,
            int totalTables,
            Double progress,
            boolean summaryAvailable
    ) {
    }

    public record DashboardActivity(
            Long id,
            String title,
            String detail,
            String actor,
            boolean success,
            Instant at
    ) {
    }

    public record DashboardSystemStatus(
            String code,
            String status,
            String detail,
            String level
    ) {
    }

    public record DashboardAdmin(
            DashboardCounters counters,
            List<DashboardElection> elections,
            List<DashboardActivity> recentActivity,
            List<DashboardSystemStatus> systemStatus,
            Instant generatedAt
    ) {
    }

    public record PollPartyResponse(Long id, String name, String acronym, String color) {
    }

    public record PollElectionResponse(Long id, String name, String type, String round, LocalDate date, String state) {
    }

    public record PollCandidateResponse(
            Long id,
            String name,
            boolean active,
            PollPartyResponse party,
            Long electionId
    ) {
    }

    public record PollResultResponse(
            Long id,
            Instant createdAt,
            Instant updatedAt,
            PollCandidateResponse candidate,
            Double percentage
    ) {
    }

    public record PollResponse(
            Long id,
            Instant createdAt,
            Instant updatedAt,
            PollElectionResponse election,
            String source,
            LocalDate date,
            Integer sampleSize,
            Double marginError,
            String methodology,
            String status,
            Double totalPercentage,
            List<PollResultResponse> results
    ) {
    }

    public record PollManagementCounters(
            long total,
            long approved,
            long pending,
            long rejected,
            double averageSample
    ) {
    }

    public record PollPage(
            List<PollResponse> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }

    public record PollManagement(
            PollManagementCounters counters,
            PollPage polls,
            List<PollElectionResponse> elections,
            List<PollCandidateResponse> candidates,
            Instant generatedAt
    ) {
    }

    public record PollImportResponse(int polls, int results) {
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
