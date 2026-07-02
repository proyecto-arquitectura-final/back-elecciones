package co.edu.elecciones.service;

import co.edu.elecciones.repository.OfficialResultRepository;
import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

@Service
public class ReportService {
    private final OfficialResultRepository results;

    public ReportService(OfficialResultRepository results) {
        this.results = results;
    }

    @Transactional(readOnly = true)
    public byte[] csv() {
        StringBuilder csv = new StringBuilder(
                "id,candidato,partido,departamento,municipio,votos,porcentaje,mesas_reportadas,origen\n"
        );
        for (var result : results.selectAll()) {
            csv.append(result.id).append(',')
                    .append(result.candidate.name).append(',')
                    .append(result.candidate.party.acronym).append(',')
                    .append(result.department).append(',')
                    .append(result.municipality).append(',')
                    .append(result.votes).append(',')
                    .append(result.percentage).append(',')
                    .append(result.reportedTables).append(',')
                    .append(result.source).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public byte[] pdf() {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, output);
            document.open();
            document.add(new Paragraph("Reporte de resultados consolidados"));
            document.add(new Paragraph("Predicción ≠ resultado oficial"));
            for (var result : results.selectAll()) {
                document.add(new Paragraph(
                        result.candidate.name + " - " + result.department + " - votos: " + result.votes
                ));
            }
            document.close();
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("No fue posible generar el PDF", exception);
        }
    }
}
