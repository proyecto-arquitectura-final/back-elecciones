package co.edu.elecciones.service;

import co.edu.elecciones.dto.Responses.PublicCandidate;
import co.edu.elecciones.dto.Responses.PublicDashboard;
import co.edu.elecciones.dto.Responses.PublicPredictionCandidate;
import co.edu.elecciones.dto.Responses.PublicPredictionDashboard;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class RuleBasedAssistantService {

    public AssistantAnswer answer(String rawQuestion,
                                  PublicDashboard dashboard,
                                  PublicPredictionDashboard prediction) {
        String question = rawQuestion == null ? "" : rawQuestion.trim().toLowerCase(Locale.ROOT);

        if (question.contains("predic") || question.contains("proyecc") || question.contains("probabilidad")) {
            return new AssistantAnswer(predictionAnswer(prediction), "PREDICTION", List.of("public_predictions"));
        }
        if (question.contains("encuesta") || question.contains("sondeo")) {
            return new AssistantAnswer(pollAnswer(prediction), "POLLS", List.of("public_predictions"));
        }
        if (question.contains("particip") || question.contains("sufrag")) {
            return new AssistantAnswer(participationAnswer(dashboard), "PARTICIPATION", List.of("public_dashboard"));
        }
        if (question.contains("compar") || question.contains("diferencia") || question.contains("brecha")) {
            return new AssistantAnswer(comparisonAnswer(dashboard, prediction), "COMPARISON",
                    List.of("public_dashboard", "public_predictions"));
        }
        if (question.contains("depart") || question.contains("municip") || question.contains("regi")
                || question.contains("territ")) {
            return new AssistantAnswer(territoryAnswer(dashboard), "TERRITORY", List.of("public_dashboard"));
        }
        if (question.contains("resultado") || question.contains("lider") || question.contains("ganando")
                || question.contains("voto")) {
            return new AssistantAnswer(resultAnswer(dashboard), "RESULTS", List.of("public_dashboard"));
        }

        return new AssistantAnswer(summaryAnswer(dashboard, prediction), "SUMMARY",
                List.of("public_dashboard", "public_predictions"));
    }

    private String resultAnswer(PublicDashboard dashboard) {
        if (dashboard.election() == null) return "No hay una elección configurada en la base de datos.";
        if (dashboard.candidates().isEmpty()) {
            return "La elección " + dashboard.election().name() + " todavía no tiene votos oficiales cargados.";
        }
        PublicCandidate leader = dashboard.candidates().get(0);
        return "En " + dashboard.election().name() + ", el liderazgo actual corresponde a "
                + leader.candidate() + " con " + format(leader.votes()) + " votos ("
                + decimal(leader.percentage()) + "%). Se han procesado "
                + decimal(dashboard.summary().percentageTables()) + "% de las mesas. Fuente: "
                + dashboard.summary().source() + ".";
    }

    private String predictionAnswer(PublicPredictionDashboard prediction) {
        if (prediction.election() == null) return "No hay una elección configurada para calcular predicciones.";
        if (prediction.candidates().isEmpty()) {
            return "No hay suficientes resultados parciales o encuestas para generar una proyección.";
        }
        PublicPredictionCandidate leader = prediction.candidates().get(0);
        return "La proyección estadística para " + prediction.election().name() + " ubica a "
                + leader.candidate() + " en " + decimal(leader.projectedPercentage())
                + "% y una probabilidad modelada de " + decimal(leader.probability())
                + "%. La confianza global del modelo es " + decimal(prediction.metrics().confidence())
                + "% con incertidumbre promedio de ±" + decimal(prediction.metrics().averageUncertainty())
                + " puntos. Esto no constituye un resultado oficial.";
    }

    private String pollAnswer(PublicPredictionDashboard prediction) {
        if (prediction.polls().isEmpty()) {
            return "No hay encuestas aplicables registradas para la elección seleccionada.";
        }
        var latest = prediction.polls().get(0);
        return "El modelo considera " + prediction.metrics().pollCount() + " encuestas y "
                + format(prediction.metrics().totalSample()) + " respuestas acumuladas. La encuesta más reciente es de "
                + latest.source() + " (" + latest.date() + "), con muestra de " + format(latest.sampleSize())
                + " y margen de error de ±" + decimal(latest.marginError()) + "%.";
    }

    private String participationAnswer(PublicDashboard dashboard) {
        if (dashboard.election() == null) return "No hay una elección configurada.";
        return "La participación registrada es " + decimal(dashboard.summary().participation())
                + "%, equivalente a " + format(dashboard.summary().voters()) + " sufragantes de "
                + format(dashboard.summary().eligibleVoters()) + " ciudadanos habilitados.";
    }

    private String comparisonAnswer(PublicDashboard dashboard, PublicPredictionDashboard prediction) {
        if (dashboard.candidates().size() < 2) {
            return "No hay al menos dos candidatos con resultados oficiales para realizar la comparación.";
        }
        PublicCandidate first = dashboard.candidates().get(0);
        PublicCandidate second = dashboard.candidates().get(1);
        String projected = prediction.candidates().size() >= 2
                ? " En la proyección, la diferencia entre los dos primeros es de "
                + decimal(prediction.candidates().get(0).projectedPercentage()
                - prediction.candidates().get(1).projectedPercentage()) + " puntos."
                : "";
        return first.candidate() + " supera actualmente a " + second.candidate() + " por "
                + format(Math.max(0, first.votes() - second.votes())) + " votos y "
                + decimal(Math.max(0, first.percentage() - second.percentage()))
                + " puntos porcentuales." + projected;
    }

    private String territoryAnswer(PublicDashboard dashboard) {
        long departments = dashboard.territories().stream().filter(item -> "DEPARTAMENTO".equals(item.level())).count();
        long municipalities = dashboard.territories().stream().filter(item -> "MUNICIPIO".equals(item.level())).count();
        return "Hay información territorial para " + departments + " departamentos y " + municipalities
                + " municipios. Puedes consultar el detalle y el líder de cada territorio en la pantalla de resultados.";
    }

    private String summaryAnswer(PublicDashboard dashboard, PublicPredictionDashboard prediction) {
        String result = resultAnswer(dashboard);
        String projection = prediction.candidates().isEmpty() ? "" : " " + predictionAnswer(prediction);
        return result + projection;
    }

    private String format(long value) {
        return String.format(new Locale("es", "CO"), "%,d", value);
    }

    private String decimal(double value) {
        return String.format(Locale.US, "%.1f", value);
    }

    public record AssistantAnswer(String answer, String intent, List<String> toolsUsed) {
    }
}
