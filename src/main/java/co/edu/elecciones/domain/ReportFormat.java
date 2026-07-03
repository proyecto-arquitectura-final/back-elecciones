package co.edu.elecciones.domain;

public enum ReportFormat {
    PDF,
    CSV,
    JSON;

    public static ReportFormat from(String value) {
        if (value == null || value.isBlank()) {
            return JSON;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Formato de reporte no soportado. Use PDF, CSV o JSON");
        }
    }
}
