-- Gestión integral de resultados oficiales.
-- Migración NO destructiva: no elimina resultados ni consolidaciones.
-- Normaliza valores nulos heredados, agrega trazabilidad de validación y refuerza integridad.

ALTER TABLE official_result
    ADD COLUMN validation_status VARCHAR(20) DEFAULT 'PENDIENTE',
    ADD COLUMN validation_message VARCHAR(500),
    ADD COLUMN validated_at TIMESTAMPTZ(6),
    ADD COLUMN validated_by VARCHAR(255);

UPDATE official_result
SET department = NULLIF(btrim(regexp_replace(department, '\s+', ' ', 'g')), ''),
    municipality = NULLIF(btrim(regexp_replace(municipality, '\s+', ' ', 'g')), ''),
    votes = COALESCE(votes, 0),
    percentage = COALESCE(percentage, 0),
    reported_tables = COALESCE(reported_tables, 0),
    total_tables = COALESCE(total_tables, 0),
    participation = COALESCE(participation, 0),
    source = COALESCE(NULLIF(btrim(regexp_replace(source, '\s+', ' ', 'g')), ''), 'CARGA_MANUAL'),
    imported_at = COALESCE(imported_at, updated_at, created_at, CURRENT_TIMESTAMP),
    validation_status = 'VALIDADO',
    validation_message = 'Validaciones de integridad superadas',
    validated_at = COALESCE(imported_at, updated_at, created_at, CURRENT_TIMESTAMP),
    validated_by = COALESCE(validated_by, 'migration-v8');

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM official_result
        WHERE votes < 0
           OR percentage < 0 OR percentage > 100
           OR reported_tables < 0
           OR total_tables < 0
           OR reported_tables > total_tables
           OR participation < 0 OR participation > 100
           OR char_length(source) > 160
           OR char_length(COALESCE(department, '')) > 120
           OR char_length(COALESCE(municipality, '')) > 120
           OR (municipality IS NOT NULL AND department IS NULL)
    ) THEN
        RAISE EXCEPTION 'Existen resultados oficiales con valores fuera de rango. Corrígelos antes de aplicar V8';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM official_result r
        JOIN candidate c ON c.id = r.candidate_id
        WHERE c.election_id <> r.election_id
    ) THEN
        RAISE EXCEPTION 'Existen resultados cuyo candidato no pertenece a la elección asociada';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM official_result
        GROUP BY election_id,
                 candidate_id,
                 lower(btrim(COALESCE(department, ''))),
                 lower(btrim(COALESCE(municipality, '')))
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Existen resultados duplicados por elección, candidato y territorio normalizado';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM official_result
        GROUP BY election_id,
                 lower(btrim(COALESCE(department, ''))),
                 lower(btrim(COALESCE(municipality, '')))
        HAVING count(DISTINCT reported_tables) > 1
            OR count(DISTINCT total_tables) > 1
            OR count(DISTINCT participation) > 1
    ) THEN
        RAISE EXCEPTION 'Existen candidatos del mismo territorio con mesas o participación inconsistentes';
    END IF;
END $$;

-- El porcentaje es un dato derivado: se reconstruye por elección y territorio.
WITH scope_totals AS (
    SELECT id,
           SUM(votes) OVER (
               PARTITION BY election_id,
                            lower(btrim(COALESCE(department, ''))),
                            lower(btrim(COALESCE(municipality, '')))
           ) AS scope_votes
    FROM official_result
)
UPDATE official_result result
SET percentage = CASE
        WHEN totals.scope_votes > 0 THEN result.votes * 100.0 / totals.scope_votes
        ELSE 0
    END
FROM scope_totals totals
WHERE totals.id = result.id;

ALTER TABLE official_result
    ALTER COLUMN votes SET DEFAULT 0,
    ALTER COLUMN votes SET NOT NULL,
    ALTER COLUMN percentage SET DEFAULT 0,
    ALTER COLUMN percentage SET NOT NULL,
    ALTER COLUMN reported_tables SET DEFAULT 0,
    ALTER COLUMN reported_tables SET NOT NULL,
    ALTER COLUMN total_tables SET DEFAULT 0,
    ALTER COLUMN total_tables SET NOT NULL,
    ALTER COLUMN participation SET DEFAULT 0,
    ALTER COLUMN participation SET NOT NULL,
    ALTER COLUMN source TYPE VARCHAR(160),
    ALTER COLUMN source SET DEFAULT 'CARGA_MANUAL',
    ALTER COLUMN source SET NOT NULL,
    ALTER COLUMN imported_at SET DEFAULT CURRENT_TIMESTAMP,
    ALTER COLUMN imported_at SET NOT NULL,
    ALTER COLUMN department TYPE VARCHAR(120),
    ALTER COLUMN municipality TYPE VARCHAR(120),
    ALTER COLUMN validation_status SET DEFAULT 'PENDIENTE',
    ALTER COLUMN validation_status SET NOT NULL;

ALTER TABLE official_result
    ADD CONSTRAINT official_result_votes_ck CHECK (votes >= 0),
    ADD CONSTRAINT official_result_percentage_ck CHECK (percentage BETWEEN 0 AND 100),
    ADD CONSTRAINT official_result_tables_ck CHECK (
        reported_tables >= 0 AND total_tables >= 0 AND reported_tables <= total_tables
    ),
    ADD CONSTRAINT official_result_participation_ck CHECK (participation BETWEEN 0 AND 100),
    ADD CONSTRAINT official_result_source_ck CHECK (btrim(source) <> '' AND char_length(source) <= 160),
    ADD CONSTRAINT official_result_territory_ck CHECK (
        municipality IS NULL OR department IS NOT NULL
    ),
    ADD CONSTRAINT official_result_validation_status_ck CHECK (
        validation_status IN ('PENDIENTE', 'VALIDADO', 'RECHAZADO')
    ),
    ADD CONSTRAINT official_result_validation_trace_ck CHECK (
        validation_status = 'PENDIENTE'
        OR (validated_at IS NOT NULL AND btrim(COALESCE(validated_by, '')) <> '')
    ),
    ADD CONSTRAINT official_result_rejection_message_ck CHECK (
        validation_status <> 'RECHAZADO'
        OR btrim(COALESCE(validation_message, '')) <> ''
    );

DROP INDEX uq_official_result_election_candidate_scope;

CREATE UNIQUE INDEX uq_official_result_election_candidate_scope
    ON official_result (
        election_id,
        candidate_id,
        lower(btrim(COALESCE(department, ''))),
        lower(btrim(COALESCE(municipality, '')))
    );

CREATE INDEX idx_official_result_management
    ON official_result(election_id, validation_status, imported_at DESC, id DESC);

CREATE INDEX idx_official_result_scope_normalized
    ON official_result(
        election_id,
        lower(btrim(COALESCE(department, ''))),
        lower(btrim(COALESCE(municipality, '')))
    );

CREATE OR REPLACE FUNCTION validate_official_result_candidate_election()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    candidate_election BIGINT;
BEGIN
    SELECT election_id INTO candidate_election
    FROM candidate
    WHERE id = NEW.candidate_id;

    IF candidate_election IS NULL OR candidate_election <> NEW.election_id THEN
        RAISE EXCEPTION 'El candidato % no pertenece a la elección %', NEW.candidate_id, NEW.election_id;
    END IF;

    RETURN NEW;
END $$;

CREATE TRIGGER trg_official_result_candidate_election
BEFORE INSERT OR UPDATE OF election_id, candidate_id ON official_result
FOR EACH ROW
EXECUTE FUNCTION validate_official_result_candidate_election();

CREATE OR REPLACE FUNCTION validate_official_result_scope_consistency()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    current_election BIGINT;
    current_department VARCHAR(120);
    current_municipality VARCHAR(120);
    current_reported_tables INTEGER;
    current_total_tables INTEGER;
    current_participation DOUBLE PRECISION;
BEGIN
    SELECT election_id,
           department,
           municipality,
           reported_tables,
           total_tables,
           participation
    INTO current_election,
         current_department,
         current_municipality,
         current_reported_tables,
         current_total_tables,
         current_participation
    FROM official_result
    WHERE id = NEW.id;

    IF NOT FOUND THEN
        RETURN NEW;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM official_result other
        WHERE other.election_id = current_election
          AND other.id <> NEW.id
          AND lower(btrim(COALESCE(other.department, '')))
              = lower(btrim(COALESCE(current_department, '')))
          AND lower(btrim(COALESCE(other.municipality, '')))
              = lower(btrim(COALESCE(current_municipality, '')))
          AND (
              other.reported_tables IS DISTINCT FROM current_reported_tables
              OR other.total_tables IS DISTINCT FROM current_total_tables
              OR other.participation IS DISTINCT FROM current_participation
          )
    ) THEN
        RAISE EXCEPTION 'Las mesas y la participación deben coincidir para todos los candidatos del mismo territorio';
    END IF;

    RETURN NEW;
END $$;

CREATE CONSTRAINT TRIGGER trg_official_result_scope_consistency
AFTER INSERT OR UPDATE ON official_result
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION validate_official_result_scope_consistency();
