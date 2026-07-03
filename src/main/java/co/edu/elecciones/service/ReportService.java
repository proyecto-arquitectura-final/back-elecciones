package co.edu.elecciones.service;

import co.edu.elecciones.domain.Election;
import co.edu.elecciones.domain.OfficialResult;
import co.edu.elecciones.domain.ReportFormat;
import co.edu.elecciones.domain.ReportGeneration;
import co.edu.elecciones.dto.AdminDtos.ReportCounters;
import co.edu.elecciones.dto.AdminDtos.ReportElection;
import co.edu.elecciones.dto.AdminDtos.ReportGenerationResponse;
import co.edu.elecciones.dto.AdminDtos.ReportManagement;
import co.edu.elecciones.dto.AdminDtos.ReportRegion;
import co.edu.elecciones.repository.ElectionRepository;
import co.edu.elecciones.repository.OfficialResultRepository;
import co.edu.elecciones.repository.ReportGenerationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ReportService {
    public record GeneratedReport(byte[] content, ReportFormat format, String filename) {
    }

    private record JsonResult(
            Long id,
            String election,
            String candidate,
            String party,
            String department,
            String municipality,
            long votes,
            double percentage,
            int reportedTables,
            int totalTables,
            double participation,
            String source,
            Instant importedAt,
            String validationStatus
    ) {
    }

    private record JsonExport(
            Long electionId,
            String election,
            Instant generatedAt,
            String generatedBy,
            ReportCounters counters,
            List<ReportRegion> regions,
            List<JsonResult> results
    ) {
    }

    private final OfficialResultRepository results;
    private final ElectionRepository elections;
    private final ReportGenerationRepository generations;
    private final AuditService audit;
    private final ObjectMapper objectMapper;

    public ReportService(
            OfficialResultRepository results,
            ElectionRepository elections,
            ReportGenerationRepository generations,
            AuditService audit,
            ObjectMapper objectMapper
    ) {
        this.results = results;
        this.elections = elections;
        this.generations = generations;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ReportManagement management(Long electionId) {
        List<Election> electionList = elections.selectAll();
        Long selectedId = resolveElectionId(electionId, electionList);
        if (selectedId == null) {
            return emptyManagement(electionList);
        }
        Election selected = elections.selectById(selectedId)
                .orElseThrow(() -> new EntityNotFoundException("Elección no encontrada"));
        List<ReportRegion> regions = mapRegions(results.selectReportRegions(selectedId));
        List<OfficialResult> detail = results.selectValidatedByElectionId(selectedId);
        ReportCounters counters = counters(detail.size(), regions);
        Map<String, ReportGenerationResponse> latest = new java.util.LinkedHashMap<>();
        for (ReportFormat format : ReportFormat.values()) {
            generations.selectLatest(selectedId, format.name())
                    .map(this::toGenerationResponse)
                    .ifPresent(value -> latest.put(format.name(), value));
        }
        return new ReportManagement(
                selected.id,
                selected.name,
                counters,
                regions,
                electionList.stream().map(this::toElection).toList(),
                latest,
                Instant.now()
        );
    }

    @Transactional
    public GeneratedReport generate(
            Long electionId,
            String rawFormat,
            HttpServletRequest request
    ) {
        ReportFormat format = ReportFormat.from(rawFormat);
        Long selectedId = resolveElectionId(electionId, elections.selectAll());
        if (selectedId == null) {
            throw new IllegalArgumentException("No hay elecciones disponibles para generar el reporte");
        }
        Election election = elections.selectById(selectedId)
                .orElseThrow(() -> new EntityNotFoundException("Elección no encontrada"));
        List<OfficialResult> detail = results.selectValidatedByElectionId(selectedId);
        if (detail.isEmpty()) {
            throw new IllegalArgumentException("La elección no tiene resultados validados para exportar");
        }
        List<ReportRegion> regions = mapRegions(results.selectReportRegions(selectedId));
        ReportCounters counters = counters(detail.size(), regions);
        String username = currentUsername();
        byte[] content = switch (format) {
            case CSV -> csv(detail);
            case PDF -> pdf(election, detail, counters, regions);
            case JSON -> json(election, detail, counters, regions, username);
        };

        ReportGeneration generation = new ReportGeneration();
        generation.election = election;
        generation.format = format;
        generation.requestedBy = username;
        generation.recordCount = (long) detail.size();
        generation.generatedAt = Instant.now();
        ReportGeneration saved = generations.save(generation);
        audit.log(
                "EXPORT_REPORT",
                "ReportGeneration",
                saved.id,
                "Reporte " + format.name() + " generado para la elección " + election.id
                        + " con " + detail.size() + " registros validados",
                request
        );
        return new GeneratedReport(
                content,
                format,
                "resultados-eleccion-" + election.id + "." + format.name().toLowerCase(Locale.ROOT)
        );
    }

    private byte[] csv(List<OfficialResult> detail) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                     .setHeader(
                             "id", "eleccion", "candidato", "partido", "departamento", "municipio",
                             "votos", "porcentaje", "mesas_reportadas", "mesas_totales",
                             "participacion", "origen", "fecha_importacion", "validacion"
                     )
                     .build())) {
            for (OfficialResult result : detail) {
                printer.printRecord(
                        result.id,
                        result.election.name,
                        result.candidate.name,
                        result.candidate.party.acronym,
                        result.department,
                        result.municipality,
                        result.votes,
                        result.percentage,
                        result.reportedTables,
                        result.totalTables,
                        result.participation,
                        result.source,
                        result.importedAt,
                        result.validationStatus.name()
                );
            }
            printer.flush();
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("No fue posible generar el archivo CSV", exception);
        }
    }

    private byte[] pdf(
            Election election,
            List<OfficialResult> detail,
            ReportCounters counters,
            List<ReportRegion> regions
    ) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, output);
            document.open();
            Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font heading = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
            Font body = FontFactory.getFont(FontFactory.HELVETICA, 8);
            document.add(new Paragraph("Reporte de resultados consolidados", title));
            document.add(new Paragraph("Elección: " + election.name));
            document.add(new Paragraph("Generado: " + Instant.now()));
            document.add(new Paragraph(
                    "Registros validados: " + counters.records()
                            + " | Votos: " + counters.votes()
                            + " | Mesas: " + counters.reportedTables() + "/" + counters.totalTables()
            ));
            document.add(new Paragraph(" "));

            PdfPTable regional = new PdfPTable(new float[]{3f, 2f, 2f, 2f, 2f});
            regional.setWidthPercentage(100);
            addHeader(regional, heading, "Región", "Votos", "Participación", "Mesas", "% procesado");
            for (ReportRegion region : regions) {
                addCells(regional, body,
                        region.region(),
                        String.valueOf(region.votes()),
                        formatDecimal(region.participation()) + "%",
                        region.reportedTables() + "/" + region.totalTables(),
                        formatDecimal(region.processedPercentage()) + "%"
                );
            }
            document.add(regional);
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(new float[]{2.5f, 2f, 1.6f, 1.6f, 1.3f, 1.2f});
            table.setWidthPercentage(100);
            addHeader(table, heading, "Candidato", "Partido", "Departamento", "Municipio", "Votos", "%");
            for (OfficialResult result : detail) {
                addCells(table, body,
                        result.candidate.name,
                        result.candidate.party.acronym,
                        safe(result.department),
                        safe(result.municipality),
                        String.valueOf(result.votes),
                        formatDecimal(result.percentage)
                );
            }
            document.add(table);
            document.close();
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("No fue posible generar el archivo PDF", exception);
        }
    }

    private byte[] json(
            Election election,
            List<OfficialResult> detail,
            ReportCounters counters,
            List<ReportRegion> regions,
            String username
    ) {
        try {
            JsonExport export = new JsonExport(
                    election.id,
                    election.name,
                    Instant.now(),
                    username,
                    counters,
                    regions,
                    detail.stream().map(result -> new JsonResult(
                            result.id,
                            result.election.name,
                            result.candidate.name,
                            result.candidate.party.acronym,
                            result.department,
                            result.municipality,
                            result.votes,
                            result.percentage,
                            result.reportedTables,
                            result.totalTables,
                            result.participation,
                            result.source,
                            result.importedAt,
                            result.validationStatus.name()
                    )).toList()
            );
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(export);
        } catch (Exception exception) {
            throw new IllegalStateException("No fue posible generar el archivo JSON", exception);
        }
    }

    private void addHeader(PdfPTable table, Font font, String... values) {
        for (String value : values) {
            PdfPCell cell = new PdfPCell(new Phrase(value, font));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }
    }

    private void addCells(PdfPTable table, Font font, String... values) {
        for (String value : values) {
            table.addCell(new Phrase(value, font));
        }
    }

    private List<ReportRegion> mapRegions(List<OfficialResultRepository.ReportRegionRow> rows) {
        return rows.stream().map(row -> {
            long reported = safeLong(row.getReportedTables());
            long total = safeLong(row.getTotalTables());
            return new ReportRegion(
                    row.getRegion(),
                    safeLong(row.getVotes()),
                    round1(row.getParticipation()),
                    reported,
                    total,
                    total == 0 ? 0 : round1(reported * 100.0 / total)
            );
        }).toList();
    }

    private ReportCounters counters(long records, List<ReportRegion> regions) {
        long votes = regions.stream().mapToLong(ReportRegion::votes).sum();
        long reported = regions.stream().mapToLong(ReportRegion::reportedTables).sum();
        long total = regions.stream().mapToLong(ReportRegion::totalTables).sum();
        return new ReportCounters(
                records,
                votes,
                regions.size(),
                reported,
                total,
                total == 0 ? 0 : round1(reported * 100.0 / total)
        );
    }

    private ReportManagement emptyManagement(List<Election> electionList) {
        return new ReportManagement(
                null,
                null,
                new ReportCounters(0, 0, 0, 0, 0, 0),
                List.of(),
                electionList.stream().map(this::toElection).toList(),
                Map.of(),
                Instant.now()
        );
    }

    private Long resolveElectionId(Long electionId, List<Election> electionList) {
        if (electionId != null) {
            return electionId;
        }
        Long withResults = results.selectLatestElectionIdWithResults();
        if (withResults != null) {
            return withResults;
        }
        return electionList.isEmpty() ? null : electionList.get(0).id;
    }

    private ReportElection toElection(Election election) {
        return new ReportElection(
                election.id,
                election.name,
                election.type == null ? null : election.type.name(),
                election.round == null ? null : election.round.name(),
                election.electionDate,
                election.state == null ? null : election.state.name()
        );
    }

    private ReportGenerationResponse toGenerationResponse(ReportGeneration generation) {
        return new ReportGenerationResponse(
                generation.id,
                generation.format.name(),
                generation.generatedAt,
                generation.requestedBy,
                generation.recordCount
        );
    }

    private String currentUsername() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || authentication.getName() == null
                ? "sistema"
                : authentication.getName();
    }

    private long safeLong(Number value) {
        return value == null ? 0L : value.longValue();
    }

    private double round1(Number value) {
        return value == null ? 0 : Math.round(value.doubleValue() * 10.0) / 10.0;
    }

    private String formatDecimal(double value) {
        return String.format(Locale.US, "%.1f", value);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "Sin dato" : value;
    }
}
