package co.edu.elecciones.service;

import co.edu.elecciones.domain.Election;
import co.edu.elecciones.domain.ElectionResultSummary;
import co.edu.elecciones.domain.ElectionState;
import co.edu.elecciones.domain.OfficialResult;
import co.edu.elecciones.dto.Responses.PublicCandidate;
import co.edu.elecciones.dto.Responses.PublicDashboard;
import co.edu.elecciones.dto.Responses.PublicElection;
import co.edu.elecciones.dto.Responses.PublicSummary;
import co.edu.elecciones.dto.Responses.PublicTerritory;
import co.edu.elecciones.repository.ElectionRepository;
import co.edu.elecciones.repository.ElectionResultSummaryRepository;
import co.edu.elecciones.repository.OfficialResultRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class PublicDashboardService {
    private final ElectionRepository elections;
    private final OfficialResultRepository officialResults;
    private final ElectionResultSummaryRepository summaries;

    public PublicDashboardService(ElectionRepository elections,
                                  OfficialResultRepository officialResults,
                                  ElectionResultSummaryRepository summaries) {
        this.elections = elections;
        this.officialResults = officialResults;
        this.summaries = summaries;
    }

    @Transactional(readOnly = true)
    public PublicDashboard load(Long electionId) {
        List<Election> allElections = elections.selectAll();
        Election selected = selectElection(allElections, electionId);
        List<PublicElection> electionOptions = allElections.stream().map(this::toPublicElection).toList();

        if (selected == null) {
            return emptyDashboard(electionOptions);
        }

        List<OfficialResult> results = officialResults.selectByElectionId(selected.id);
        Optional<ElectionResultSummary> persistedSummary = summaries.selectByElectionId(selected.id);
        ResultData resultData = buildResultData(results, persistedSummary.orElse(null));
        List<PublicCandidate> candidates = buildCandidates(resultData.candidates(), resultData.summary());

        return new PublicDashboard(
                toPublicElection(selected),
                electionOptions,
                resultData.summary(),
                candidates,
                resultData.territories()
        );
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(Long electionId) {
        PublicDashboard dashboard = load(electionId);
        StringWriter writer = new StringWriter();

        try (CSVPrinter csv = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                .setHeader(
                        "tipo",
                        "ranking",
                        "candidato",
                        "partido",
                        "departamento",
                        "municipio",
                        "votos",
                        "porcentaje",
                        "mesas_reportadas",
                        "mesas_totales",
                        "participacion",
                        "lider"
                )
                .build())) {
            PublicSummary summary = dashboard.summary();
            csv.printRecord(
                    "RESUMEN",
                    "",
                    "",
                    "",
                    "",
                    "",
                    summary.candidateVotes(),
                    "",
                    summary.reportedTables(),
                    summary.totalTables(),
                    summary.participation(),
                    ""
            );

            for (PublicCandidate candidate : dashboard.candidates()) {
                csv.printRecord(
                        "CANDIDATO",
                        candidate.rank(),
                        candidate.candidate(),
                        candidate.party(),
                        "",
                        "",
                        candidate.votes(),
                        candidate.percentage(),
                        "",
                        "",
                        "",
                        ""
                );
            }

            for (PublicTerritory territory : dashboard.territories()) {
                csv.printRecord(
                        territory.level(),
                        "",
                        "",
                        "",
                        territory.department(),
                        territory.municipality(),
                        territory.votes(),
                        "",
                        territory.reportedTables(),
                        territory.totalTables(),
                        territory.participation(),
                        territory.leader()
                );
            }
        } catch (IOException exception) {
            throw new IllegalStateException("No fue posible generar el CSV público de resultados", exception);
        }

        // BOM UTF-8 para que Excel reconozca correctamente tildes y ñ.
        return ("\uFEFF" + writer).getBytes(StandardCharsets.UTF_8);
    }

    private PublicDashboard emptyDashboard(List<PublicElection> elections) {
        return new PublicDashboard(
                null,
                elections,
                new PublicSummary(
                        0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0,
                        0, 0, 0,
                        0, true, "SIN_DATOS", null
                ),
                List.of(),
                List.of()
        );
    }

    private Election selectElection(List<Election> all, Long requestedId) {
        if (requestedId != null) {
            return all.stream().filter(item -> Objects.equals(item.id, requestedId)).findFirst().orElse(null);
        }

        return all.stream()
                .filter(item -> item.state == ElectionState.ABIERTA || item.state == ElectionState.EN_CONTEO)
                .findFirst()
                .orElseGet(() -> all.stream().findFirst().orElse(null));
    }

    private ResultData buildResultData(List<OfficialResult> results, ElectionResultSummary persistedSummary) {
        Map<Long, CandidateAggregate> candidateMap = new LinkedHashMap<>();
        Map<String, TerritoryAggregate> municipalityMap = new LinkedHashMap<>();
        Instant lastUpdated = persistedSummary == null ? null : persistedSummary.importedAt;
        long candidateVotes = 0;
        Set<String> sources = new LinkedHashSet<>();

        for (OfficialResult result : results) {
            long votes = nonNegative(result.votes);
            candidateVotes += votes;

            CandidateAggregate candidate = candidateMap.computeIfAbsent(
                    result.candidate.id,
                    ignored -> new CandidateAggregate(
                            result.candidate.id,
                            result.candidate.name,
                            result.candidate.party.name,
                            result.candidate.party.acronym,
                            result.candidate.party.color
                    )
            );
            candidate.votes += votes;

            String department = normalize(result.department, "Nacional");
            String municipality = normalize(result.municipality, "Total departamental");
            String key = department + "|" + municipality;
            TerritoryAggregate territory = municipalityMap.computeIfAbsent(
                    key,
                    ignored -> new TerritoryAggregate(department, municipality)
            );
            territory.reportedTables = Math.max(territory.reportedTables, nonNegative(result.reportedTables));
            territory.totalTables = Math.max(territory.totalTables, nonNegative(result.totalTables));
            territory.participation = Math.max(territory.participation, boundedPercentage(result.participation));
            territory.votes += votes;
            territory.candidateVotes.merge(result.candidate.name, votes, Long::sum);

            if (result.source != null && !result.source.isBlank()) {
                sources.add(result.source.trim());
            }

            Instant itemUpdated = result.importedAt != null
                    ? result.importedAt
                    : result.updatedAt != null ? result.updatedAt : result.createdAt;
            lastUpdated = latest(lastUpdated, itemUpdated);
        }

        Map<String, TerritoryAggregate> departmentMap = new LinkedHashMap<>();
        for (TerritoryAggregate municipality : municipalityMap.values()) {
            TerritoryAggregate department = departmentMap.computeIfAbsent(
                    municipality.department,
                    ignored -> new TerritoryAggregate(municipality.department, null)
            );
            department.reportedTables += municipality.reportedTables;
            department.totalTables += municipality.totalTables;
            department.votes += municipality.votes;
            double participationWeight = municipality.totalTables > 0 ? municipality.totalTables : 1;
            department.participationWeighted += municipality.participation * participationWeight;
            department.participationWeight += participationWeight;
            municipality.candidateVotes.forEach((name, votes) ->
                    department.candidateVotes.merge(name, votes, Long::sum));
        }

        int territoryReportedTables = municipalityMap.values().stream()
                .mapToInt(item -> item.reportedTables)
                .sum();
        int territoryTotalTables = municipalityMap.values().stream()
                .mapToInt(item -> item.totalTables)
                .sum();
        double fallbackParticipation = weightedParticipation(municipalityMap.values());

        long eligibleVoters = persistedSummary == null ? 0 : nonNegative(persistedSummary.eligibleVoters);
        long voters = persistedSummary == null ? candidateVotes : nonNegative(persistedSummary.totalVoters);
        long blankVotes = persistedSummary == null ? 0 : nonNegative(persistedSummary.blankVotes);
        long nullVotes = persistedSummary == null ? 0 : nonNegative(persistedSummary.nullVotes);
        long unmarkedVotes = persistedSummary == null ? 0 : nonNegative(persistedSummary.unmarkedVotes);
        long validVotes = persistedSummary == null
                ? candidateVotes
                : nonNegative(persistedSummary.validVotes);
        int reportedTables = persistedSummary == null
                ? territoryReportedTables
                : nonNegative(persistedSummary.reportedTables);
        int totalTables = persistedSummary == null
                ? territoryTotalTables
                : nonNegative(persistedSummary.totalTables);
        double participation = eligibleVoters > 0
                ? percent(voters, eligibleVoters)
                : fallbackParticipation;
        long consistencyDifference = validVotes - (candidateVotes + blankVotes);
        boolean consistent = consistencyDifference == 0;
        String source = persistedSummary != null && persistedSummary.source != null && !persistedSummary.source.isBlank()
                ? persistedSummary.source
                : sources.isEmpty() ? "SIN_FUENTE" : String.join(", ", sources);

        List<PublicTerritory> territories = new ArrayList<>();
        departmentMap.values().stream()
                .sorted(Comparator.comparingLong((TerritoryAggregate item) -> item.votes).reversed()
                        .thenComparing(item -> item.department))
                .map(item -> toTerritory("DEPARTAMENTO", item))
                .forEach(territories::add);
        municipalityMap.values().stream()
                .sorted(Comparator.comparing((TerritoryAggregate item) -> item.department)
                        .thenComparing(item -> item.municipality))
                .map(item -> toTerritory("MUNICIPIO", item))
                .forEach(territories::add);

        int departmentCount = (int) departmentMap.keySet().stream()
                .filter(name -> !"Nacional".equalsIgnoreCase(name))
                .count();
        int municipalityCount = (int) municipalityMap.values().stream()
                .filter(item -> !"Total departamental".equalsIgnoreCase(item.municipality))
                .count();

        PublicSummary summary = new PublicSummary(
                candidateVotes,
                voters,
                eligibleVoters,
                validVotes,
                blankVotes,
                nullVotes,
                unmarkedVotes,
                reportedTables,
                totalTables,
                percent(reportedTables, totalTables),
                round(participation),
                departmentCount,
                municipalityCount,
                results.size(),
                consistencyDifference,
                consistent,
                source,
                lastUpdated
        );

        return new ResultData(summary, candidateMap, territories);
    }

    private List<PublicCandidate> buildCandidates(Map<Long, CandidateAggregate> aggregates, PublicSummary summary) {
        long percentageBase = summary.validVotes() > 0 ? summary.validVotes() : summary.candidateVotes();
        List<CandidateAggregate> ordered = aggregates.values().stream()
                .sorted(Comparator.comparingLong((CandidateAggregate item) -> item.votes).reversed()
                        .thenComparing(item -> item.name))
                .toList();
        long leaderVotes = ordered.isEmpty() ? 0 : ordered.get(0).votes;
        double leaderPercentage = percentageBase == 0 ? 0 : leaderVotes * 100.0 / percentageBase;

        List<PublicCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < ordered.size(); index++) {
            CandidateAggregate item = ordered.get(index);
            double percentage = percentageBase == 0 ? 0 : item.votes * 100.0 / percentageBase;
            candidates.add(new PublicCandidate(
                    index + 1,
                    item.id,
                    item.name,
                    item.party,
                    item.acronym,
                    item.color,
                    item.votes,
                    round(percentage),
                    Math.max(0, leaderVotes - item.votes),
                    round(Math.max(0, leaderPercentage - percentage))
            ));
        }
        return candidates;
    }

    private PublicTerritory toTerritory(String level, TerritoryAggregate item) {
        double participation = item.participationWeight > 0
                ? item.participationWeighted / item.participationWeight
                : item.participation;
        String leader = item.candidateVotes.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("Sin resultados");
        return new PublicTerritory(
                level,
                item.department,
                item.municipality,
                item.reportedTables,
                item.totalTables,
                percent(item.reportedTables, item.totalTables),
                round(participation),
                leader,
                item.votes
        );
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

    private double weightedParticipation(Iterable<TerritoryAggregate> territories) {
        double totalWeight = 0;
        double weighted = 0;
        for (TerritoryAggregate item : territories) {
            double weight = item.totalTables > 0 ? item.totalTables : 1;
            totalWeight += weight;
            weighted += item.participation * weight;
        }
        return totalWeight == 0 ? 0 : weighted / totalWeight;
    }

    private Instant latest(Instant current, Instant candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null || candidate.isAfter(current) ? candidate : current;
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private int nonNegative(Integer value) {
        return Math.max(0, Optional.ofNullable(value).orElse(0));
    }

    private long nonNegative(Long value) {
        return Math.max(0L, Optional.ofNullable(value).orElse(0L));
    }

    private double boundedPercentage(Double value) {
        return Math.max(0, Math.min(100, Optional.ofNullable(value).orElse(0.0)));
    }

    private double percent(long numerator, long denominator) {
        return denominator == 0 ? 0 : round(numerator * 100.0 / denominator);
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static final class CandidateAggregate {
        private final Long id;
        private final String name;
        private final String party;
        private final String acronym;
        private final String color;
        private long votes;

        private CandidateAggregate(Long id, String name, String party, String acronym, String color) {
            this.id = id;
            this.name = name;
            this.party = party;
            this.acronym = acronym;
            this.color = color;
        }
    }

    private static final class TerritoryAggregate {
        private final String department;
        private final String municipality;
        private int reportedTables;
        private int totalTables;
        private double participation;
        private double participationWeighted;
        private double participationWeight;
        private long votes;
        private final Map<String, Long> candidateVotes = new LinkedHashMap<>();

        private TerritoryAggregate(String department, String municipality) {
            this.department = department;
            this.municipality = municipality;
        }
    }

    private record ResultData(PublicSummary summary,
                              Map<Long, CandidateAggregate> candidates,
                              List<PublicTerritory> territories) {
    }
}
