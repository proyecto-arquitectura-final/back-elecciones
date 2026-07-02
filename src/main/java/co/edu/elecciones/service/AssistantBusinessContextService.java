package co.edu.elecciones.service;

import co.edu.elecciones.domain.AssistantMessage;
import co.edu.elecciones.dto.Responses.PublicCandidate;
import co.edu.elecciones.dto.Responses.PublicDashboard;
import co.edu.elecciones.dto.Responses.PublicPollEvidence;
import co.edu.elecciones.dto.Responses.PublicPredictionCandidate;
import co.edu.elecciones.dto.Responses.PublicPredictionDashboard;
import co.edu.elecciones.dto.Responses.PublicTerritory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class AssistantBusinessContextService {

    private final int territoryLimit;
    private final int candidateLimit;
    private final int pollLimit;

    public AssistantBusinessContextService(
            @Value("${app.gemini.context-territory-limit:20}") int territoryLimit,
            @Value("${app.gemini.context-candidate-limit:12}") int candidateLimit,
            @Value("${app.gemini.context-poll-limit:10}") int pollLimit) {
        this.territoryLimit = Math.max(1, territoryLimit);
        this.candidateLimit = Math.max(2, candidateLimit);
        this.pollLimit = Math.max(1, pollLimit);
    }

    public BusinessContext build(String question,
                                 PublicDashboard dashboard,
                                 PublicPredictionDashboard prediction,
                                 List<AssistantMessage> history) {
        StringBuilder context = new StringBuilder(12_000);
        Set<String> sources = new LinkedHashSet<>();

        context.append("ELECCION SELECCIONADA\n");
        if (dashboard.election() == null) {
            context.append("No hay una elección configurada.\n");
        } else {
            context.append("- ID: ").append(dashboard.election().id()).append('\n');
            context.append("- Nombre: ").append(safe(dashboard.election().name())).append('\n');
            context.append("- Tipo: ").append(safe(dashboard.election().type())).append('\n');
            context.append("- Vuelta: ").append(safe(dashboard.election().round())).append('\n');
            context.append("- Fecha: ").append(dashboard.election().date()).append('\n');
            context.append("- Estado: ").append(safe(dashboard.election().state())).append("\n\n");
        }

        context.append("RESUMEN OFICIAL PERSISTIDO\n");
        if (dashboard.summary() != null) {
            var summary = dashboard.summary();
            context.append("- Votos de candidatos: ").append(summary.candidateVotes()).append('\n');
            context.append("- Sufragantes: ").append(summary.voters()).append('\n');
            context.append("- Potencial electoral: ").append(summary.eligibleVoters()).append('\n');
            context.append("- Votos válidos: ").append(summary.validVotes()).append('\n');
            context.append("- Votos en blanco: ").append(summary.blankVotes()).append('\n');
            context.append("- Votos nulos: ").append(summary.nullVotes()).append('\n');
            context.append("- Tarjetas no marcadas: ").append(summary.unmarkedVotes()).append('\n');
            context.append("- Mesas reportadas: ").append(summary.reportedTables()).append(" de ")
                    .append(summary.totalTables()).append(" (")
                    .append(format(summary.percentageTables())).append("%)\n");
            context.append("- Participación: ").append(format(summary.participation())).append("%\n");
            context.append("- Registros oficiales: ").append(summary.resultRecords()).append('\n');
            context.append("- Consistencia: ").append(summary.consistent() ? "CONSISTENTE" : "REVISAR")
                    .append("; diferencia=").append(summary.consistencyDifference()).append('\n');
            context.append("- Fuente: ").append(safe(summary.source())).append('\n');
            context.append("- Última actualización: ").append(summary.lastUpdated()).append("\n\n");
            sources.add("Resultados oficiales y resumen electoral almacenados en PostgreSQL");
        }

        context.append("RESULTADOS POR CANDIDATO\n");
        dashboard.candidates().stream().limit(candidateLimit).forEach(candidate -> appendCandidate(context, candidate));
        context.append('\n');
        if (!dashboard.candidates().isEmpty()) sources.add("Resultados oficiales por candidato");

        context.append("MODELO DE PREDICCION\n");
        if (prediction.metrics() == null) {
            context.append("No hay métricas de predicción disponibles.\n\n");
        } else {
            var metrics = prediction.metrics();
            context.append("- Modo: ").append(safe(metrics.modelMode())).append('\n');
            context.append("- Calidad de datos: ").append(safe(metrics.dataQuality())).append('\n');
            context.append("- Cobertura procesada: ").append(format(metrics.processedPercentage())).append("%\n");
            context.append("- Confianza del modelo: ").append(format(metrics.confidence())).append("%\n");
            context.append("- Incertidumbre promedio: ±").append(format(metrics.averageUncertainty())).append(" puntos\n");
            context.append("- Peso de resultados oficiales: ").append(format(metrics.officialWeight())).append("%\n");
            context.append("- Peso de encuestas: ").append(format(metrics.pollWeight())).append("%\n");
            context.append("- Encuestas consideradas: ").append(metrics.pollCount()).append('\n');
            context.append("- Muestra acumulada: ").append(metrics.totalSample()).append("\n\n");
            sources.add("Modelo estadístico público calculado por el backend");
        }

        context.append("PROYECCIONES POR CANDIDATO\n");
        prediction.candidates().stream().limit(candidateLimit)
                .forEach(candidate -> appendPrediction(context, candidate));
        context.append('\n');

        context.append("ENCUESTAS CONSIDERADAS\n");
        prediction.polls().stream().limit(pollLimit).forEach(poll -> appendPoll(context, poll));
        context.append('\n');
        if (!prediction.polls().isEmpty()) sources.add("Encuestas registradas en PostgreSQL");

        List<PublicTerritory> relevantTerritories = relevantTerritories(question, dashboard.territories());
        context.append("COBERTURA TERRITORIAL RELEVANTE\n");
        relevantTerritories.forEach(territory -> context.append("- ")
                .append(safe(territory.level())).append(": ")
                .append(safe(territory.department()))
                .append(territory.municipality() == null || territory.municipality().isBlank()
                        ? "" : " / " + territory.municipality())
                .append("; líder=").append(safe(territory.leader()))
                .append("; votos líder=").append(territory.votes())
                .append("; mesas=").append(territory.reportedTables()).append('/').append(territory.totalTables())
                .append("; procesado=").append(format(territory.processedPercentage())).append('%')
                .append("; participación=").append(format(territory.participation())).append("%\n"));
        context.append('\n');
        if (!relevantTerritories.isEmpty()) sources.add("Agregaciones territoriales de resultados");

        context.append("HISTORIAL RECIENTE DE LA CONVERSACION\n");
        if (history.isEmpty()) {
            context.append("Sin mensajes anteriores.\n");
        } else {
            history.forEach(message -> context.append("- ")
                    .append("USER".equals(message.role) ? "Usuario" : "Asistente")
                    .append(": ").append(limit(message.content, 900)).append('\n'));
        }

        return new BusinessContext(context.toString(), List.copyOf(sources));
    }

    private List<PublicTerritory> relevantTerritories(String question, List<PublicTerritory> territories) {
        if (territories == null || territories.isEmpty()) return List.of();
        String normalizedQuestion = normalize(question);
        List<PublicTerritory> exact = territories.stream()
                .filter(item -> containsTerritory(normalizedQuestion, item))
                .sorted(Comparator.comparingLong(PublicTerritory::votes).reversed())
                .limit(territoryLimit)
                .toList();
        if (!exact.isEmpty()) return exact;
        return territories.stream()
                .sorted(Comparator.comparingLong(PublicTerritory::votes).reversed())
                .limit(territoryLimit)
                .toList();
    }

    private boolean containsTerritory(String normalizedQuestion, PublicTerritory territory) {
        String department = normalize(territory.department());
        String municipality = normalize(territory.municipality());
        return (!department.isBlank() && normalizedQuestion.contains(department))
                || (!municipality.isBlank() && normalizedQuestion.contains(municipality));
    }

    private void appendCandidate(StringBuilder target, PublicCandidate candidate) {
        target.append("- #").append(candidate.rank()).append(' ')
                .append(safe(candidate.candidate())).append(" (")
                .append(safe(candidate.party())).append(")")
                .append("; votos=").append(candidate.votes())
                .append("; porcentaje=").append(format(candidate.percentage())).append('%')
                .append("; brecha_votos=").append(candidate.gapVotes())
                .append("; brecha_puntos=").append(format(candidate.gapPercentage())).append('\n');
    }

    private void appendPrediction(StringBuilder target, PublicPredictionCandidate candidate) {
        target.append("- #").append(candidate.rank()).append(' ')
                .append(safe(candidate.candidate()))
                .append("; actual=").append(format(candidate.currentPercentage())).append('%')
                .append("; promedio_encuestas=").append(format(candidate.pollAverage())).append('%')
                .append("; proyectado=").append(format(candidate.projectedPercentage())).append('%')
                .append("; probabilidad_liderazgo=").append(format(candidate.probability())).append('%')
                .append("; incertidumbre=±").append(format(candidate.uncertaintyMargin()))
                .append("; tendencia=").append(format(candidate.trend()))
                .append("; observaciones_encuestas=").append(candidate.pollObservations()).append('\n');
    }

    private void appendPoll(StringBuilder target, PublicPollEvidence poll) {
        target.append("- ").append(safe(poll.source()))
                .append("; fecha=").append(poll.date())
                .append("; muestra=").append(poll.sampleSize())
                .append("; margen=±").append(format(poll.marginError()))
                .append("; metodología=").append(safe(poll.methodology())).append('\n');
    }

    private String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "No disponible" : value.replace('\n', ' ').trim();
    }

    private String limit(String value, int max) {
        String safe = value == null ? "" : value.replace('\n', ' ').trim();
        return safe.length() <= max ? safe : safe.substring(0, max) + "…";
    }

    private String format(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    public record BusinessContext(String text, List<String> sources) {
    }
}
