package co.edu.elecciones.service;

import co.edu.elecciones.domain.Candidate;
import co.edu.elecciones.domain.Election;
import co.edu.elecciones.domain.ElectionResultSummary;
import co.edu.elecciones.domain.PollResult;
import co.edu.elecciones.dto.Responses.PredictionFactor;
import co.edu.elecciones.dto.Responses.PublicElection;
import co.edu.elecciones.dto.Responses.PublicPollEvidence;
import co.edu.elecciones.dto.Responses.PublicPredictionCandidate;
import co.edu.elecciones.dto.Responses.PublicPredictionDashboard;
import co.edu.elecciones.dto.Responses.PublicPredictionMetrics;
import co.edu.elecciones.repository.CandidateRepository;
import co.edu.elecciones.repository.ElectionRepository;
import co.edu.elecciones.repository.ElectionResultSummaryRepository;
import co.edu.elecciones.repository.OfficialResultRepository;
import co.edu.elecciones.repository.PollResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PublicPredictionService {
    private static final double EPSILON = 0.000001;
    private static final List<String> FALLBACK_COLORS = List.of(
            "#2563eb", "#dc2626", "#16a34a", "#ea580c", "#7c3aed", "#0891b2"
    );

    private final ElectionRepository elections;
    private final ElectionResultSummaryRepository summaries;
    private final OfficialResultRepository officialResults;
    private final PollResultRepository pollResults;
    private final CandidateRepository candidates;

    public PublicPredictionService(
            ElectionRepository elections,
            ElectionResultSummaryRepository summaries,
            OfficialResultRepository officialResults,
            PollResultRepository pollResults,
            CandidateRepository candidates
    ) {
        this.elections = elections;
        this.summaries = summaries;
        this.officialResults = officialResults;
        this.pollResults = pollResults;
        this.candidates = candidates;
    }

    @Transactional(readOnly = true)
    public PublicPredictionDashboard load(Long electionId) {
        List<Election> electionList = elections.selectAll();
        Election election = resolveElection(electionList, electionId);
        List<PublicElection> publicElections = electionList.stream().map(this::toPublicElection).toList();

        if (election == null) {
            return new PublicPredictionDashboard(
                    null,
                    publicElections,
                    new PublicPredictionMetrics(0, 0, 0, 0, 0, "SIN_DATOS", "SIN_ELECCION", 0, 0),
                    List.of(),
                    List.of(),
                    List.of(),
                    Instant.now()
            );
        }

        ElectionResultSummary summary = summaries.selectByElectionId(election.id).orElse(null);
        double processedPercentage = percentage(
                summary == null ? 0 : nonNegative(summary.reportedTables),
                summary == null ? 0 : nonNegative(summary.totalTables)
        );
        double coverage = processedPercentage / 100.0;

        Map<Long, CandidateInput> inputs = buildCandidateInputs(election);
        applyOfficialResults(inputs, election.id);
        PollData pollData = applyPolls(inputs, election);

        long totalVotes = inputs.values().stream().mapToLong(input -> input.votes).sum();
        boolean hasOfficialResults = totalVotes > 0;
        boolean hasPolls = pollData.pollCount > 0;
        String modelMode = modelMode(hasOfficialResults, hasPolls);

        double officialWeight = officialWeight(hasOfficialResults, hasPolls, coverage);
        double pollWeight = hasPolls ? 1.0 - officialWeight : 0;

        for (CandidateInput input : inputs.values()) {
            input.currentPercentage = totalVotes == 0 ? 0 : input.votes * 100.0 / totalVotes;
            input.pollAverage = input.pollWeight == 0 ? 0 : input.pollWeightedPercentage / input.pollWeight;

            if (hasOfficialResults && hasPolls && input.pollWeight > 0) {
                input.projectedPercentage = input.currentPercentage * officialWeight + input.pollAverage * pollWeight;
            } else if (hasOfficialResults) {
                input.projectedPercentage = input.currentPercentage;
            } else if (input.pollWeight > 0) {
                input.projectedPercentage = input.pollAverage;
            } else {
                input.projectedPercentage = 0;
            }

            double pollMargin = input.pollWeight == 0 ? 0 : input.pollWeightedMargin / input.pollWeight;
            double countUncertainty = hasOfficialResults ? 6.5 * Math.sqrt(Math.max(0, 1.0 - coverage)) : 6.5;
            double missingPollPenalty = hasPolls && input.pollWeight == 0 ? 3.5 : 0;
            input.uncertainty = Math.sqrt(
                    Math.pow(pollMargin * Math.max(0.25, pollWeight), 2)
                            + Math.pow(countUncertainty * Math.max(0.25, officialWeight), 2)
                            + Math.pow(missingPollPenalty, 2)
            );
        }

        normalizeProjection(inputs);
        calculateProbabilities(inputs);

        List<CandidateInput> ordered = inputs.values().stream()
                .filter(input -> input.votes > 0 || input.pollWeight > 0)
                .sorted(Comparator.comparingDouble((CandidateInput input) -> input.projectedPercentage).reversed()
                        .thenComparing(input -> input.name))
                .toList();

        double averageUncertainty = ordered.stream().mapToDouble(input -> input.uncertainty).average().orElse(0);
        double pollCandidateCoverage = ordered.isEmpty() ? 0 : ordered.stream()
                .filter(input -> input.pollWeight > 0)
                .count() * 1.0 / ordered.size();
        double confidence = confidence(coverage, pollData.pollCount, pollCandidateCoverage, averageUncertainty, modelMode);
        String dataQuality = dataQuality(processedPercentage, pollData.pollCount, pollCandidateCoverage, modelMode);

        List<PublicPredictionCandidate> predictionCandidates = new ArrayList<>();
        for (int index = 0; index < ordered.size(); index++) {
            CandidateInput input = ordered.get(index);
            predictionCandidates.add(new PublicPredictionCandidate(
                    index + 1,
                    input.id,
                    input.name,
                    input.party,
                    input.acronym,
                    normalizeColor(input.color, index),
                    input.votes,
                    round(input.currentPercentage),
                    round(input.pollAverage),
                    round(input.projectedPercentage),
                    round(input.probability),
                    round(input.uncertainty),
                    round(input.projectedPercentage - input.currentPercentage),
                    input.pollObservations
            ));
        }

        PublicPredictionMetrics metrics = new PublicPredictionMetrics(
                round(processedPercentage),
                round(confidence),
                round(averageUncertainty),
                pollData.pollCount,
                pollData.totalSample,
                modelMode,
                dataQuality,
                round(officialWeight * 100),
                round(pollWeight * 100)
        );

        return new PublicPredictionDashboard(
                toPublicElection(election),
                publicElections,
                metrics,
                predictionCandidates,
                pollData.evidence,
                buildFactors(metrics, predictionCandidates.size(), pollData),
                Instant.now()
        );
    }

    private Election resolveElection(List<Election> electionList, Long electionId) {
        if (electionId != null) {
            return elections.selectById(electionId).orElseThrow();
        }
        return electionList.stream().findFirst().orElse(null);
    }

    private Map<Long, CandidateInput> buildCandidateInputs(Election election) {
        Map<Long, CandidateInput> result = new LinkedHashMap<>();
        for (Candidate candidate : candidates.selectAll()) {
            if (!candidate.active
                    || candidate.election == null
                    || !candidate.election.id.equals(election.id)) {
                continue;
            }
            result.put(candidate.id, new CandidateInput(
                    candidate.id,
                    candidate.name,
                    candidate.party == null ? "Sin partido" : candidate.party.name,
                    candidate.party == null ? "" : candidate.party.acronym,
                    candidate.party == null ? null : candidate.party.color
            ));
        }
        return result;
    }

    private void applyOfficialResults(Map<Long, CandidateInput> inputs, Long electionId) {
        officialResults.selectByElectionId(electionId).forEach(result -> {
            Candidate candidate = result.candidate;
            CandidateInput input = inputs.computeIfAbsent(candidate.id, ignored -> new CandidateInput(
                    candidate.id,
                    candidate.name,
                    candidate.party == null ? "Sin partido" : candidate.party.name,
                    candidate.party == null ? "" : candidate.party.acronym,
                    candidate.party == null ? null : candidate.party.color
            ));
            input.votes += nonNegative(result.votes);
        });
    }

    private PollData applyPolls(Map<Long, CandidateInput> inputs, Election election) {
        Map<Long, PublicPollEvidence> evidence = new LinkedHashMap<>();
        LocalDate referenceDate = election.electionDate == null
                ? LocalDate.now()
                : (election.electionDate.isBefore(LocalDate.now()) ? election.electionDate : LocalDate.now());
        LocalDate minimumDate = referenceDate.minusYears(2);

        for (PollResult result : pollResults.selectApprovedByElectionId(election.id)) {
            if (result.candidate == null
                    || result.candidate.election == null
                    || !result.candidate.election.id.equals(election.id)
                    || result.poll == null) {
                continue;
            }
            if (result.poll.date != null
                    && (result.poll.date.isBefore(minimumDate)
                    || (election.electionDate != null && result.poll.date.isAfter(election.electionDate)))) {
                continue;
            }

            Candidate candidate = result.candidate;
            CandidateInput input = inputs.computeIfAbsent(candidate.id, ignored -> new CandidateInput(
                    candidate.id,
                    candidate.name,
                    candidate.party == null ? "Sin partido" : candidate.party.name,
                    candidate.party == null ? "" : candidate.party.acronym,
                    candidate.party == null ? null : candidate.party.color
            ));

            long ageDays = result.poll.date == null
                    ? 180
                    : Math.max(0, ChronoUnit.DAYS.between(result.poll.date, referenceDate));
            double recencyWeight = Math.exp(-ageDays / 45.0);
            double sampleWeight = Math.sqrt(Math.max(100, Optional.ofNullable(result.poll.sampleSize).orElse(100)));
            double margin = Math.max(0.5, Optional.ofNullable(result.poll.marginError).orElse(3.0));
            double qualityWeight = 1.0 / margin;
            double weight = Math.max(EPSILON, recencyWeight * sampleWeight * qualityWeight);

            input.pollWeightedPercentage += nonNegative(result.percentage) * weight;
            input.pollWeightedMargin += margin * weight;
            input.pollWeight += weight;
            input.pollObservations++;

            evidence.putIfAbsent(result.poll.id, new PublicPollEvidence(
                    result.poll.id,
                    result.poll.source,
                    result.poll.date,
                    Optional.ofNullable(result.poll.sampleSize).orElse(0),
                    round(Optional.ofNullable(result.poll.marginError).orElse(0.0)),
                    result.poll.methodology
            ));
        }

        List<PublicPollEvidence> orderedEvidence = evidence.values().stream()
                .sorted(Comparator.comparing(PublicPollEvidence::date, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(PublicPollEvidence::id, Comparator.reverseOrder()))
                .toList();
        long totalSample = orderedEvidence.stream().mapToLong(PublicPollEvidence::sampleSize).sum();
        return new PollData(orderedEvidence.size(), totalSample, orderedEvidence);
    }

    private double officialWeight(boolean hasOfficialResults, boolean hasPolls, double coverage) {
        if (!hasOfficialResults) return 0;
        if (!hasPolls) return 1;
        return clamp(0.20 + 0.80 * Math.sqrt(clamp(coverage, 0, 1)), 0.20, 0.98);
    }

    private void normalizeProjection(Map<Long, CandidateInput> inputs) {
        double total = inputs.values().stream().mapToDouble(input -> input.projectedPercentage).sum();
        if (total <= EPSILON) return;
        inputs.values().forEach(input -> input.projectedPercentage = input.projectedPercentage * 100.0 / total);
    }

    private void calculateProbabilities(Map<Long, CandidateInput> inputs) {
        List<CandidateInput> candidatesWithData = inputs.values().stream()
                .filter(input -> input.projectedPercentage > 0)
                .toList();
        if (candidatesWithData.isEmpty()) return;

        double averageUncertainty = candidatesWithData.stream()
                .mapToDouble(input -> input.uncertainty)
                .average()
                .orElse(3);
        double temperature = Math.max(1.25, averageUncertainty * 1.35);
        double maxProjection = candidatesWithData.stream()
                .mapToDouble(input -> input.projectedPercentage)
                .max()
                .orElse(0);

        double denominator = 0;
        for (CandidateInput input : candidatesWithData) {
            input.probabilityWeight = Math.exp((input.projectedPercentage - maxProjection) / temperature);
            denominator += input.probabilityWeight;
        }
        for (CandidateInput input : candidatesWithData) {
            input.probability = denominator == 0 ? 0 : input.probabilityWeight * 100.0 / denominator;
        }
    }

    private List<PredictionFactor> buildFactors(
            PublicPredictionMetrics metrics,
            int candidateCount,
            PollData pollData
    ) {
        String resultStatus = metrics.processedPercentage() >= 80 ? "ALTA" : metrics.processedPercentage() >= 30 ? "MEDIA" : "BAJA";
        String pollStatus = pollData.pollCount >= 3 ? "ALTA" : pollData.pollCount > 0 ? "MEDIA" : "BAJA";
        String uncertaintyStatus = metrics.averageUncertainty() <= 2.5 ? "ALTA" : metrics.averageUncertainty() <= 5 ? "MEDIA" : "BAJA";

        return List.of(
                new PredictionFactor(
                        "COBERTURA",
                        "Cobertura del escrutinio",
                        round(metrics.processedPercentage()) + "%",
                        "Porcentaje de mesas reportadas usado para ponderar los resultados parciales.",
                        resultStatus
                ),
                new PredictionFactor(
                        "ENCUESTAS",
                        "Evidencia de encuestas",
                        pollData.pollCount + " fuentes",
                        pollData.totalSample + " respuestas acumuladas en las encuestas consideradas.",
                        pollStatus
                ),
                new PredictionFactor(
                        "INCERTIDUMBRE",
                        "Incertidumbre estimada",
                        "±" + round(metrics.averageUncertainty()) + "%",
                        "Combina margen de error de encuestas y cobertura pendiente del escrutinio.",
                        uncertaintyStatus
                ),
                new PredictionFactor(
                        "CANDIDATOS",
                        "Candidatos modelados",
                        String.valueOf(candidateCount),
                        "Solo se incluyen candidatos con votos oficiales o mediciones de encuesta disponibles.",
                        candidateCount > 1 ? "ALTA" : "BAJA"
                )
        );
    }

    private double confidence(
            double coverage,
            int pollCount,
            double pollCandidateCoverage,
            double averageUncertainty,
            String modelMode
    ) {
        if ("SIN_DATOS".equals(modelMode)) return 0;
        double score = 25;
        score += clamp(coverage, 0, 1) * 50;
        score += Math.min(15, pollCount * 3.5);
        score += pollCandidateCoverage * 10;
        score -= averageUncertainty * 1.7;
        if ("SOLO_ENCUESTAS".equals(modelMode)) score = Math.min(score, 68);
        if ("SOLO_RESULTADOS".equals(modelMode) && coverage < 0.15) score = Math.min(score, 55);
        return clamp(score, 0, 99.5);
    }

    private String dataQuality(double processed, int polls, double candidateCoverage, String mode) {
        if ("SIN_DATOS".equals(mode)) return "SIN_DATOS";
        if (processed >= 80 && polls >= 2 && candidateCoverage >= 0.75) return "ALTA";
        if ((processed >= 30 || polls >= 2) && candidateCoverage >= 0.5) return "MEDIA";
        return "BAJA";
    }

    private String modelMode(boolean results, boolean polls) {
        if (results && polls) return "RESULTADOS_Y_ENCUESTAS";
        if (results) return "SOLO_RESULTADOS";
        if (polls) return "SOLO_ENCUESTAS";
        return "SIN_DATOS";
    }

    private PublicElection toPublicElection(Election election) {
        return new PublicElection(
                election.id,
                election.name,
                election.type == null ? null : election.type.name(),
                election.round == null ? null : election.round.name(),
                election.electionDate,
                election.state == null ? null : election.state.name()
        );
    }

    private String normalizeColor(String color, int index) {
        if (color != null && color.matches("^#[0-9a-fA-F]{6}$")) return color;
        return FALLBACK_COLORS.get(index % FALLBACK_COLORS.size());
    }

    private long nonNegative(Long value) {
        return Math.max(0L, Optional.ofNullable(value).orElse(0L));
    }

    private int nonNegative(Integer value) {
        return Math.max(0, Optional.ofNullable(value).orElse(0));
    }

    private double nonNegative(Double value) {
        return Math.max(0, Optional.ofNullable(value).orElse(0.0));
    }

    private double percentage(long numerator, long denominator) {
        return denominator == 0 ? 0 : numerator * 100.0 / denominator;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static final class CandidateInput {
        private final Long id;
        private final String name;
        private final String party;
        private final String acronym;
        private final String color;
        private long votes;
        private double currentPercentage;
        private double pollAverage;
        private double projectedPercentage;
        private double probability;
        private double uncertainty;
        private double pollWeightedPercentage;
        private double pollWeightedMargin;
        private double pollWeight;
        private double probabilityWeight;
        private int pollObservations;

        private CandidateInput(Long id, String name, String party, String acronym, String color) {
            this.id = id;
            this.name = name;
            this.party = party;
            this.acronym = acronym;
            this.color = color;
        }
    }

    private record PollData(int pollCount, long totalSample, List<PublicPollEvidence> evidence) {
    }
}
