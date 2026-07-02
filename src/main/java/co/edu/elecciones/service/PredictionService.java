package co.edu.elecciones.service;

import co.edu.elecciones.domain.ElectionType;
import co.edu.elecciones.dto.Responses.PredictionItem;
import co.edu.elecciones.repository.OfficialResultRepository;
import co.edu.elecciones.repository.PollResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PredictionService {
    private final PollResultRepository pollResults;
    private final OfficialResultRepository officialResults;

    public PredictionService(PollResultRepository pollResults, OfficialResultRepository officialResults) {
        this.pollResults = pollResults;
        this.officialResults = officialResults;
    }

    @Transactional(readOnly = true)
    public List<PredictionItem> byPolls() {
        return byPolls(null);
    }

    @Transactional(readOnly = true)
    public List<PredictionItem> byPolls(ElectionType electionType) {
        Map<String, double[]> accumulated = new LinkedHashMap<>();

        for (var result : pollResults.selectAllWithDetails()) {
            if (electionType != null && result.candidate.electionType != electionType) {
                continue;
            }

            LocalDate pollDate = result.poll.date == null ? LocalDate.now() : result.poll.date;
            long ageInDays = Math.max(0, ChronoUnit.DAYS.between(pollDate, LocalDate.now()));
            double recencyWeight = 1.0 / (ageInDays + 1.0);
            double sampleWeight = Math.sqrt(Math.max(1, Optional.ofNullable(result.poll.sampleSize).orElse(1)));
            double marginWeight = 1.0 / Math.max(0.1, Optional.ofNullable(result.poll.marginError).orElse(1.0));
            double weight = recencyWeight * sampleWeight * marginWeight;

            String key = result.candidate.name + "|" + result.candidate.party.name;
            double[] values = accumulated.computeIfAbsent(key, ignored -> new double[3]);
            values[0] += Optional.ofNullable(result.percentage).orElse(0.0) * weight;
            values[1] += weight;
            values[2] += Optional.ofNullable(result.poll.marginError).orElse(0.0) * weight;
        }

        List<PredictionItem> averages = accumulated.entrySet().stream()
                .map(entry -> {
                    String[] parts = entry.getKey().split("\\|", 2);
                    double totalWeight = entry.getValue()[1];
                    double average = totalWeight == 0 ? 0 : entry.getValue()[0] / totalWeight;
                    double uncertainty = totalWeight == 0 ? 0 : entry.getValue()[2] / totalWeight;
                    return new PredictionItem(
                            parts[0],
                            parts.length > 1 ? parts[1] : "",
                            round(average),
                            round(average),
                            0,
                            round(uncertainty)
                    );
                })
                .sorted(Comparator.comparing(PredictionItem::projectedPercentage).reversed())
                .toList();

        double totalProjected = averages.stream().mapToDouble(PredictionItem::projectedPercentage).sum();
        return averages.stream()
                .map(item -> new PredictionItem(
                        item.candidate(),
                        item.party(),
                        item.currentPercentage(),
                        item.projectedPercentage(),
                        totalProjected == 0 ? 0 : round(item.projectedPercentage() * 100.0 / totalProjected),
                        item.uncertaintyMargin()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PredictionItem> byPartialResults() {
        return buildPartialPrediction(
                Optional.ofNullable(officialResults.selectTotalVotes()).orElse(0L),
                officialResults.selectVotesGroupedByCandidate()
        );
    }

    @Transactional(readOnly = true)
    public List<PredictionItem> byPartialResults(Long electionId) {
        List<OfficialResultRepository.CandidateVoteAggregate> aggregates =
                officialResults.selectVotesGroupedByCandidateForElection(electionId);
        long totalVotes = aggregates.stream()
                .map(OfficialResultRepository.CandidateVoteAggregate::getVotes)
                .filter(java.util.Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
        return buildPartialPrediction(totalVotes, aggregates);
    }

    private List<PredictionItem> buildPartialPrediction(
            long totalVotes,
            List<OfficialResultRepository.CandidateVoteAggregate> aggregates
    ) {
        List<PredictionItem> current = aggregates.stream()
                .map(item -> {
                    long candidateVotes = Optional.ofNullable(item.getVotes()).orElse(0L);
                    double percentage = totalVotes == 0 ? 0 : candidateVotes * 100.0 / totalVotes;
                    return new PredictionItem(
                            item.getCandidate(),
                            item.getParty(),
                            round(percentage),
                            round(percentage),
                            round(percentage),
                            0
                    );
                })
                .sorted(Comparator.comparing(PredictionItem::projectedPercentage).reversed())
                .toList();

        double totalPercentage = current.stream().mapToDouble(PredictionItem::currentPercentage).sum();
        return current.stream()
                .map(item -> new PredictionItem(
                        item.candidate(),
                        item.party(),
                        item.currentPercentage(),
                        item.currentPercentage(),
                        totalPercentage == 0 ? 0 : round(item.currentPercentage() * 100.0 / totalPercentage),
                        0
                ))
                .toList();
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
