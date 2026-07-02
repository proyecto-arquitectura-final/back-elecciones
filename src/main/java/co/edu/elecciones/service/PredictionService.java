package co.edu.elecciones.service;

import co.edu.elecciones.dto.Responses.*;
import co.edu.elecciones.repository.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.*;

@Service
public class PredictionService {
    private final PollRepository polls;
    private final OfficialResultRepository results;

    public PredictionService(PollRepository p, OfficialResultRepository r) {
        polls = p;
        results = r;
    }

    public List<PredictionItem> byPolls() {
        var all = polls.findAll();
        Map<String, double[]> acc = new LinkedHashMap<>();
        for (var poll : all) {
            double recency = 1.0 / Math.max(1, ChronoUnit.DAYS.between(poll.date, LocalDate.now()) + 1);
            double sample = Math.sqrt(Optional.ofNullable(poll.sampleSize).orElse(1));
            double w = recency * sample;
            for (var r : poll.results) {
                String k = r.candidate.name + "|" + r.candidate.party.name;
                acc.computeIfAbsent(k, x -> new double[2]);
                acc.get(k)[0] += r.percentage * w;
                acc.get(k)[1] += w;
            }
        }
        return acc.entrySet().stream().map(e -> {
            var parts = e.getKey().split("");
            double avg = e.getValue()[1] == 0 ? 0 : e.getValue()[0] / e.getValue()[1];
            return new PredictionItem(parts[0], parts[1], avg, avg, Math.min(99, 55 + avg), 3.0);
        }).sorted(Comparator.comparing(PredictionItem::projectedPercentage).reversed()).toList();
    }

    public List<PredictionItem> byPartialResults() {
        return results.findAll().stream().collect(Collectors.groupingBy(r -> r.candidate.name + "|" + r.candidate.party.name, Collectors.summingLong(r -> r.votes))).entrySet().stream().map(e -> {
            long total = results.findAll().stream().mapToLong(r -> r.votes).sum();
            double pct = total == 0 ? 0 : e.getValue() * 100.0 / total;
            var p = e.getKey().split("");
            return new PredictionItem(p[0], p[1], pct, Math.min(100, pct + 0.7), Math.min(99, 60 + pct), 2.5);
        }).sorted(Comparator.comparing(PredictionItem::projectedPercentage).reversed()).toList();
    }
}
