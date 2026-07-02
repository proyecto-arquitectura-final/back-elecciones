-- ATENCIÓN: migración destructiva de datos.
-- Su propósito es dejar una base coherente y suficientemente rica para probar:
--   * resultados públicos;
--   * predicciones;
--   * contexto territorial;
--   * preguntas y respuestas del asistente Gemini.
--
-- No elimina estructuras ni el historial de Flyway, pero sí vacía todas las tablas
-- funcionales de la aplicación. Debe ejecutarse únicamente en el ambiente que se
-- quiera reinicializar con datos sintéticos de prueba.

TRUNCATE TABLE
    assistant_message,
    assistant_session,
    poll_result,
    poll,
    official_result,
    election_result_summary,
    candidate,
    party,
    audit_event,
    app_user,
    election
RESTART IDENTITY CASCADE;

-- -----------------------------------------------------------------------------
-- Usuarios de prueba
-- -----------------------------------------------------------------------------
-- Credenciales:
--   Administrador: admin@elecciones.gov.co / Admin2026!
--   Analista:      analista@elecciones.gov.co / Analista2026!

INSERT INTO app_user (
    created_at,
    updated_at,
    active,
    email,
    name,
    password,
    role
)
VALUES
    (
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP,
        TRUE,
        'admin@elecciones.gov.co',
        'Administrador Electoral',
        '$2y$10$8FbdwNLWP7ANJIu8784nsu801EFMgvJV1/KxHS2C5YHi99qfXqJJ2',
        'ADMINISTRADOR'
    ),
    (
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP,
        TRUE,
        'analista@elecciones.gov.co',
        'Analista Electoral',
        '$2y$10$fjKp0tzZ0Ejsas9xyTtWAuWVlXj.cJ7nO5TcRRXrJrxNaDTTz.6zC',
        'ANALISTA'
    );

-- -----------------------------------------------------------------------------
-- Elección activa de prueba
-- -----------------------------------------------------------------------------

INSERT INTO election (
    created_at,
    updated_at,
    election_date,
    name,
    round,
    state,
    type
)
VALUES (
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    DATE '2026-05-31',
    'Presidencia Colombia 2026 - Primera vuelta',
    'PRIMERA',
    'EN_CONTEO',
    'PRESIDENCIA'
);

-- -----------------------------------------------------------------------------
-- Partidos y candidatos ficticios
-- -----------------------------------------------------------------------------

INSERT INTO party (
    created_at,
    updated_at,
    acronym,
    active,
    color,
    foundation_year,
    name
)
VALUES
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'PCC', TRUE, '#2563EB', 2012, 'Pacto Cívico Colombiano'),
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'AN',  TRUE, '#DC2626', 2008, 'Alianza Nacional'),
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'VC',  TRUE, '#16A34A', 2016, 'Verde Colombia'),
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'MC',  TRUE, '#F59E0B', 2010, 'Movimiento Ciudadano');

INSERT INTO candidate (
    created_at,
    updated_at,
    active,
    department,
    election_type,
    municipality,
    name,
    vice_president_name,
    party_id
)
SELECT
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    TRUE,
    candidate_data.department,
    'PRESIDENCIA',
    candidate_data.municipality,
    candidate_data.candidate_name,
    candidate_data.vice_president_name,
    p.id
FROM (
    VALUES
        ('María Fernández'::VARCHAR, 'Carlos Rojas'::VARCHAR, 'Bogotá D.C.'::VARCHAR, 'Bogotá'::VARCHAR, 'PCC'::VARCHAR),
        ('Juan Rodríguez'::VARCHAR,  'Ana Torres'::VARCHAR,   'Antioquia'::VARCHAR,  'Medellín'::VARCHAR, 'AN'::VARCHAR),
        ('Laura Gómez'::VARCHAR,     'Miguel Castro'::VARCHAR,'Valle del Cauca'::VARCHAR, 'Cali'::VARCHAR, 'VC'::VARCHAR),
        ('Andrés Pérez'::VARCHAR,    'Sofía Ramírez'::VARCHAR,'Nariño'::VARCHAR, 'Pasto'::VARCHAR, 'MC'::VARCHAR)
) AS candidate_data(candidate_name, vice_president_name, department, municipality, party_acronym)
JOIN party p
  ON p.acronym = candidate_data.party_acronym;

-- -----------------------------------------------------------------------------
-- Resumen consolidado de la elección
-- -----------------------------------------------------------------------------
-- La suma de votos por candidato es 2.480.000.
-- Con 120.000 votos en blanco se obtienen 2.600.000 votos válidos.
-- Los 50.000 nulos y 30.000 no marcados completan 2.680.000 sufragantes.

INSERT INTO election_result_summary (
    created_at,
    updated_at,
    election_id,
    eligible_voters,
    total_voters,
    valid_votes,
    blank_votes,
    null_votes,
    unmarked_votes,
    reported_tables,
    total_tables,
    source,
    imported_at
)
SELECT
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    e.id,
    4000000,
    2680000,
    2600000,
    120000,
    50000,
    30000,
    8200,
    10000,
    'DATOS_SINTETICOS_PARA_PRUEBAS_GEMINI',
    CURRENT_TIMESTAMP - INTERVAL '5 minutes'
FROM election e
WHERE e.name = 'Presidencia Colombia 2026 - Primera vuelta';

-- -----------------------------------------------------------------------------
-- Resultados oficiales sintéticos por municipio y candidato
-- -----------------------------------------------------------------------------
-- No se insertan totales nacionales adicionales porque el backend agrega estas
-- filas territoriales para construir el consolidado nacional.

WITH result_data AS (
    SELECT *
    FROM (
        VALUES
            ('Bogotá D.C.'::VARCHAR, 'Bogotá'::VARCHAR,         'María Fernández'::VARCHAR, 310000::BIGINT, 68.4::DOUBLE PRECISION, 2500::INTEGER, 3000::INTEGER),
            ('Bogotá D.C.',          'Bogotá',                  'Juan Rodríguez',           250000,         68.4,                   2500,          3000),
            ('Bogotá D.C.',          'Bogotá',                  'Laura Gómez',               160000,         68.4,                   2500,          3000),
            ('Bogotá D.C.',          'Bogotá',                  'Andrés Pérez',               80000,         68.4,                   2500,          3000),

            ('Antioquia',            'Medellín',                'María Fernández',           140000,         66.1,                   1400,          1700),
            ('Antioquia',            'Medellín',                'Juan Rodríguez',            190000,         66.1,                   1400,          1700),
            ('Antioquia',            'Medellín',                'Laura Gómez',                80000,         66.1,                   1400,          1700),
            ('Antioquia',            'Medellín',                'Andrés Pérez',               40000,         66.1,                   1400,          1700),

            ('Valle del Cauca',      'Cali',                    'María Fernández',           150000,         67.3,                   1200,          1450),
            ('Valle del Cauca',      'Cali',                    'Juan Rodríguez',            120000,         67.3,                   1200,          1450),
            ('Valle del Cauca',      'Cali',                    'Laura Gómez',                75000,         67.3,                   1200,          1450),
            ('Valle del Cauca',      'Cali',                    'Andrés Pérez',               35000,         67.3,                   1200,          1450),

            ('Atlántico',            'Barranquilla',            'María Fernández',            90000,         65.7,                    800,           950),
            ('Atlántico',            'Barranquilla',            'Juan Rodríguez',            100000,         65.7,                    800,           950),
            ('Atlántico',            'Barranquilla',            'Laura Gómez',                40000,         65.7,                    800,           950),
            ('Atlántico',            'Barranquilla',            'Andrés Pérez',               20000,         65.7,                    800,           950),

            ('Santander',            'Bucaramanga',              'María Fernández',            70000,         69.2,                    600,           750),
            ('Santander',            'Bucaramanga',              'Juan Rodríguez',             60000,         69.2,                    600,           750),
            ('Santander',            'Bucaramanga',              'Laura Gómez',                40000,         69.2,                    600,           750),
            ('Santander',            'Bucaramanga',              'Andrés Pérez',               20000,         69.2,                    600,           750),

            ('Bolívar',              'Cartagena',                'María Fernández',            65000,         64.8,                    550,           700),
            ('Bolívar',              'Cartagena',                'Juan Rodríguez',             55000,         64.8,                    550,           700),
            ('Bolívar',              'Cartagena',                'Laura Gómez',                35000,         64.8,                    550,           700),
            ('Bolívar',              'Cartagena',                'Andrés Pérez',               15000,         64.8,                    550,           700),

            ('Cundinamarca',         'Soacha',                   'María Fernández',            55000,         66.9,                    500,           650),
            ('Cundinamarca',         'Soacha',                   'Juan Rodríguez',             40000,         66.9,                    500,           650),
            ('Cundinamarca',         'Soacha',                   'Laura Gómez',                30000,         66.9,                    500,           650),
            ('Cundinamarca',         'Soacha',                   'Andrés Pérez',               15000,         66.9,                    500,           650),

            ('Nariño',               'Pasto',                    'María Fernández',            40000,         70.1,                    650,           800),
            ('Nariño',               'Pasto',                    'Juan Rodríguez',             15000,         70.1,                    650,           800),
            ('Nariño',               'Pasto',                    'Laura Gómez',                10000,         70.1,                    650,           800),
            ('Nariño',               'Pasto',                    'Andrés Pérez',               35000,         70.1,                    650,           800)
    ) AS data(
        department,
        municipality,
        candidate_name,
        votes,
        participation,
        reported_tables,
        total_tables
    )
), calculated AS (
    SELECT
        data.*,
        data.votes * 100.0
            / SUM(data.votes) OVER (PARTITION BY data.department, data.municipality) AS percentage
    FROM result_data data
)
INSERT INTO official_result (
    created_at,
    updated_at,
    department,
    imported_at,
    municipality,
    participation,
    percentage,
    reported_tables,
    source,
    total_tables,
    votes,
    candidate_id,
    election_id
)
SELECT
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    calculated.department,
    CURRENT_TIMESTAMP - INTERVAL '5 minutes',
    calculated.municipality,
    calculated.participation,
    calculated.percentage,
    calculated.reported_tables,
    'SIMULACION_ELECTORAL_GEMINI',
    calculated.total_tables,
    calculated.votes,
    c.id,
    e.id
FROM calculated
JOIN candidate c
  ON c.name = calculated.candidate_name
 AND c.election_type = 'PRESIDENCIA'
JOIN election e
  ON e.name = 'Presidencia Colombia 2026 - Primera vuelta';

-- -----------------------------------------------------------------------------
-- Encuestas sintéticas para alimentar el modelo de predicción
-- -----------------------------------------------------------------------------

INSERT INTO poll (
    created_at,
    updated_at,
    date,
    margin_error,
    methodology,
    sample_size,
    source
)
VALUES
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, DATE '2026-04-25', 2.5, 'Mixta: presencial y telefónica',        1600, 'Opinión Nacional'),
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, DATE '2026-05-04', 2.3, 'Telefónica con cobertura nacional',      2200, 'Pulso Electoral'),
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, DATE '2026-05-12', 2.4, 'Mixta con muestreo estratificado',       1800, 'Datos País'),
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, DATE '2026-05-20', 2.2, 'Presencial, telefónica y panel digital', 2000, 'Observatorio Ciudadano'),
    (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, DATE '2026-05-28', 2.0, 'Mixta con cobertura urbana y rural',     2500, 'Centro de Estudios Electorales');

WITH poll_data AS (
    SELECT *
    FROM (
        VALUES
            ('Opinión Nacional'::VARCHAR,            DATE '2026-04-25', 'María Fernández'::VARCHAR, 34.7::DOUBLE PRECISION),
            ('Opinión Nacional',                     DATE '2026-04-25', 'Juan Rodríguez',           35.4),
            ('Opinión Nacional',                     DATE '2026-04-25', 'Laura Gómez',               19.8),
            ('Opinión Nacional',                     DATE '2026-04-25', 'Andrés Pérez',              10.1),

            ('Pulso Electoral',                      DATE '2026-05-04', 'María Fernández',           35.2),
            ('Pulso Electoral',                      DATE '2026-05-04', 'Juan Rodríguez',            34.9),
            ('Pulso Electoral',                      DATE '2026-05-04', 'Laura Gómez',               20.1),
            ('Pulso Electoral',                      DATE '2026-05-04', 'Andrés Pérez',               9.8),

            ('Datos País',                           DATE '2026-05-12', 'María Fernández',           35.9),
            ('Datos País',                           DATE '2026-05-12', 'Juan Rodríguez',            34.2),
            ('Datos País',                           DATE '2026-05-12', 'Laura Gómez',               19.7),
            ('Datos País',                           DATE '2026-05-12', 'Andrés Pérez',              10.2),

            ('Observatorio Ciudadano',               DATE '2026-05-20', 'María Fernández',           36.8),
            ('Observatorio Ciudadano',               DATE '2026-05-20', 'Juan Rodríguez',            33.8),
            ('Observatorio Ciudadano',               DATE '2026-05-20', 'Laura Gómez',               19.4),
            ('Observatorio Ciudadano',               DATE '2026-05-20', 'Andrés Pérez',              10.0),

            ('Centro de Estudios Electorales',       DATE '2026-05-28', 'María Fernández',           37.5),
            ('Centro de Estudios Electorales',       DATE '2026-05-28', 'Juan Rodríguez',            33.2),
            ('Centro de Estudios Electorales',       DATE '2026-05-28', 'Laura Gómez',               18.8),
            ('Centro de Estudios Electorales',       DATE '2026-05-28', 'Andrés Pérez',              10.5)
    ) AS data(source, poll_date, candidate_name, percentage)
)
INSERT INTO poll_result (
    created_at,
    updated_at,
    percentage,
    candidate_id,
    poll_id
)
SELECT
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    poll_data.percentage,
    c.id,
    p.id
FROM poll_data
JOIN poll p
  ON p.source = poll_data.source
 AND p.date = poll_data.poll_date
JOIN candidate c
  ON c.name = poll_data.candidate_name
 AND c.election_type = 'PRESIDENCIA';

-- -----------------------------------------------------------------------------
-- Auditoría mínima para probar el dashboard administrativo
-- -----------------------------------------------------------------------------

INSERT INTO audit_event (
    action,
    at,
    details,
    entity,
    entity_id,
    ip,
    success,
    username
)
VALUES
    (
        'SEED_GEMINI_DEMO',
        CURRENT_TIMESTAMP,
        'Reinicio controlado y carga de datos sintéticos para pruebas del asistente Gemini.',
        'DATABASE',
        NULL,
        '127.0.0.1',
        TRUE,
        'flyway'
    ),
    (
        'IMPORT_RESULTS',
        CURRENT_TIMESTAMP - INTERVAL '5 minutes',
        'Carga sintética de resultados para ocho municipios y cuatro candidatos.',
        'ELECTION',
        (SELECT id FROM election WHERE name = 'Presidencia Colombia 2026 - Primera vuelta'),
        '127.0.0.1',
        TRUE,
        'sistema'
    ),
    (
        'IMPORT_POLLS',
        CURRENT_TIMESTAMP - INTERVAL '4 minutes',
        'Carga de cinco encuestas sintéticas para validación del modelo de predicción.',
        'POLL',
        NULL,
        '127.0.0.1',
        TRUE,
        'sistema'
    );

-- -----------------------------------------------------------------------------
-- Validaciones de consistencia: la migración falla si la semilla quedó incompleta
-- -----------------------------------------------------------------------------

DO $$
DECLARE
    v_candidate_count         INTEGER;
    v_result_count            INTEGER;
    v_poll_count              INTEGER;
    v_poll_result_count       INTEGER;
    v_candidate_votes         BIGINT;
    v_valid_votes             BIGINT;
    v_blank_votes             BIGINT;
BEGIN
    SELECT COUNT(*) INTO v_candidate_count FROM candidate;
    SELECT COUNT(*) INTO v_result_count FROM official_result;
    SELECT COUNT(*) INTO v_poll_count FROM poll;
    SELECT COUNT(*) INTO v_poll_result_count FROM poll_result;
    SELECT COALESCE(SUM(votes), 0) INTO v_candidate_votes FROM official_result;
    SELECT valid_votes, blank_votes
      INTO v_valid_votes, v_blank_votes
      FROM election_result_summary;

    IF v_candidate_count <> 4 THEN
        RAISE EXCEPTION 'Semilla inválida: se esperaban 4 candidatos y se encontraron %', v_candidate_count;
    END IF;

    IF v_result_count <> 32 THEN
        RAISE EXCEPTION 'Semilla inválida: se esperaban 32 resultados territoriales y se encontraron %', v_result_count;
    END IF;

    IF v_poll_count <> 5 OR v_poll_result_count <> 20 THEN
        RAISE EXCEPTION
            'Semilla inválida: se esperaban 5 encuestas y 20 resultados; se encontraron % y %',
            v_poll_count,
            v_poll_result_count;
    END IF;

    IF v_candidate_votes + v_blank_votes <> v_valid_votes THEN
        RAISE EXCEPTION
            'Semilla inconsistente: votos de candidatos (%) + blancos (%) no coincide con votos válidos (%)',
            v_candidate_votes,
            v_blank_votes,
            v_valid_votes;
    END IF;
END $$;
