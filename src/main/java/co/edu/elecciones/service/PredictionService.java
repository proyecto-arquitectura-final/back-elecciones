package co.edu.elecciones.service;

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
        Map<String, double[]> accumulated = new LinkedHashMap<>();

        for (var result : pollResults.selectAllWithDetails()) {
            LocalDate pollDate = result.poll.date == null ? LocalDate.now() : result.poll.date;
            long ageInDays = Math.max(0, ChronoUnit.DAYS.between(pollDate, LocalDate.now()));
            double recencyWeight = 1.0 / (ageInDays + 1.0);
            double sampleWeight = Math.sqrt(Optional.ofNullable(result.poll.sampleSize).orElse(1));
            double weight = recencyWeight * sampleWeight;

            String key = result.candidate.name + "|" + result.candidate.party.name;
            double[] values = accumulated.computeIfAbsent(key, ignored -> new double[2]);
            values[0] += Optional.ofNullable(result.percentage).orElse(0.0) * weight;
            values[1] += weight;
        }

        return accumulated.entrySet().stream()
                .map(entry -> {
                    String[] parts = entry.getKey().split("\\|", 2);
                    double totalWeight = entry.getValue()[1];
                    double average = totalWeight == 0 ? 0 : entry.getValue()[0] / totalWeight;
                    return new PredictionItem(
                            parts[0],
                            parts.length > 1 ? parts[1] : "",
                            average,
                            average,
                            Math.min(99, 55 + average),
                            3.0
                    );
                })
                .sorted(Comparator.comparing(PredictionItem::projectedPercentage).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PredictionItem> byPartialResults() {
        long totalVotes = Optional.ofNullable(officialResults.selectTotalVotes()).orElse(0L);

        return officialResults.selectVotesGroupedByCandidate().stream()
                .map(item -> {
                    long candidateVotes = Optional.ofNullable(item.getVotes()).orElse(0L);
                    double percentage = totalVotes == 0 ? 0 : candidateVotes * 100.0 / totalVotes;
                    return new PredictionItem(
                            item.getCandidate(),
                            item.getParty(),
                            percentage,
                            Math.min(100, percentage + 0.7),
                            Math.min(99, 60 + percentage),
                            2.5
                    );
                })
                .sorted(Comparator.comparing(PredictionItem::projectedPercentage).reversed())
                .toList();
    }
}
