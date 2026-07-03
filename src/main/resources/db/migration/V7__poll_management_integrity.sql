-- Gestión integral de encuestas.
-- Migración NO destructiva: no elimina encuestas ni resultados.
-- Normaliza datos heredados, vincula cada encuesta con una elección y agrega estado explícito.

ALTER TABLE poll
    ADD COLUMN election_id BIGINT,
    ADD COLUMN status VARCHAR(20) DEFAULT 'PENDIENTE';

UPDATE poll
SET source = btrim(regexp_replace(source, '\s+', ' ', 'g'))
WHERE source IS NOT NULL;

UPDATE poll
SET methodology = btrim(regexp_replace(methodology, '\s+', ' ', 'g'))
WHERE methodology IS NOT NULL;

-- Cuando los resultados de una encuesta pertenecen a una sola elección, esa es la asociación correcta.
WITH inferred AS (
    SELECT pr.poll_id, min(c.election_id) AS election_id
    FROM poll_result pr
    JOIN candidate c ON c.id = pr.candidate_id
    GROUP BY pr.poll_id
    HAVING count(DISTINCT c.election_id) = 1
)
UPDATE poll p
SET election_id = inferred.election_id
FROM inferred
WHERE p.id = inferred.poll_id;

-- Compatibilidad con encuestas heredadas sin resultados: se asocian a la elección operativa más reciente.
-- Quedan PENDIENTES para que un administrador las revise antes de usarlas en predicciones.
UPDATE poll p
SET election_id = fallback.id
FROM (
    SELECT e.id
    FROM election e
    ORDER BY
        CASE e.state
            WHEN 'EN_CONTEO' THEN 1
            WHEN 'ABIERTA' THEN 2
            WHEN 'CONFIGURADA' THEN 3
            WHEN 'CERRADA' THEN 4
            ELSE 5
        END,
        e.election_date DESC,
        e.id DESC
    LIMIT 1
) fallback
WHERE p.election_id IS NULL;

-- Las encuestas heredadas completas ya eran consumidas por el modelo; se conservan como aprobadas.
UPDATE poll p
SET status = CASE
    WHEN EXISTS (SELECT 1 FROM poll_result pr WHERE pr.poll_id = p.id)
        THEN 'APROBADA'
    ELSE 'PENDIENTE'
END;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM poll
        WHERE election_id IS NULL
           OR source IS NULL
           OR btrim(source) = ''
           OR char_length(source) > 160
           OR date IS NULL
           OR sample_size IS NULL
           OR sample_size <= 0
           OR margin_error IS NULL
           OR margin_error <= 0
           OR margin_error > 20
           OR methodology IS NULL
           OR btrim(methodology) = ''
           OR char_length(methodology) > 500
    ) THEN
        RAISE EXCEPTION 'Existen encuestas incompletas o con valores fuera de rango. Corrígelas antes de aplicar V7';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM poll
        GROUP BY election_id, lower(btrim(source)), date
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Existen encuestas duplicadas para la misma elección, fuente y fecha. Deben depurarse antes de aplicar V7';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM poll_result
        WHERE percentage IS NULL OR percentage < 0 OR percentage > 100
    ) THEN
        RAISE EXCEPTION 'Existen resultados de encuesta con porcentajes nulos o fuera del rango 0 a 100';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM poll_result
        GROUP BY poll_id, candidate_id
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Existen candidatos duplicados dentro de una misma encuesta. Deben depurarse antes de aplicar V7';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM poll_result pr
        JOIN poll p ON p.id = pr.poll_id
        JOIN candidate c ON c.id = pr.candidate_id
        WHERE p.election_id <> c.election_id
    ) THEN
        RAISE EXCEPTION 'Existen resultados cuyos candidatos no pertenecen a la elección asociada a la encuesta';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM poll p
        LEFT JOIN poll_result pr ON pr.poll_id = p.id
        GROUP BY p.id, p.status
        HAVING sum(coalesce(pr.percentage, 0)) > 100.01
            OR (p.status = 'APROBADA' AND sum(coalesce(pr.percentage, 0)) <= 0)
    ) THEN
        RAISE EXCEPTION 'Existen encuestas con suma de porcentajes superior a 100 o aprobadas sin resultados positivos';
    END IF;
END $$;

ALTER TABLE poll
    ALTER COLUMN election_id SET NOT NULL,
    ALTER COLUMN status SET NOT NULL,
    ALTER COLUMN source TYPE VARCHAR(160),
    ALTER COLUMN source SET NOT NULL,
    ALTER COLUMN date SET NOT NULL,
    ALTER COLUMN sample_size SET NOT NULL,
    ALTER COLUMN margin_error SET NOT NULL,
    ALTER COLUMN methodology TYPE VARCHAR(500),
    ALTER COLUMN methodology SET NOT NULL;

ALTER TABLE poll
    ADD CONSTRAINT poll_election_fk
        FOREIGN KEY (election_id) REFERENCES election(id),
    ADD CONSTRAINT poll_status_ck
        CHECK (status IN ('PENDIENTE', 'APROBADA', 'RECHAZADA')),
    ADD CONSTRAINT poll_source_ck
        CHECK (btrim(source) <> '' AND char_length(source) <= 160),
    ADD CONSTRAINT poll_sample_size_ck
        CHECK (sample_size BETWEEN 1 AND 10000000),
    ADD CONSTRAINT poll_margin_error_ck
        CHECK (margin_error > 0 AND margin_error <= 20),
    ADD CONSTRAINT poll_methodology_ck
        CHECK (btrim(methodology) <> '' AND char_length(methodology) <= 500);

ALTER TABLE poll_result
    ALTER COLUMN percentage SET NOT NULL,
    ADD CONSTRAINT poll_result_percentage_ck
        CHECK (percentage BETWEEN 0 AND 100);

CREATE UNIQUE INDEX uq_poll_election_source_date
    ON poll(election_id, lower(btrim(source)), date);

CREATE INDEX idx_poll_election_status_date
    ON poll(election_id, status, date DESC, id DESC);

CREATE UNIQUE INDEX uq_poll_result_poll_candidate
    ON poll_result(poll_id, candidate_id);

-- Refuerza en PostgreSQL la misma regla validada por el servicio: candidato y encuesta deben ser de la misma elección.
CREATE OR REPLACE FUNCTION validate_poll_result_election()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    poll_election BIGINT;
    candidate_election BIGINT;
BEGIN
    SELECT election_id INTO poll_election FROM poll WHERE id = NEW.poll_id;
    SELECT election_id INTO candidate_election FROM candidate WHERE id = NEW.candidate_id;

    IF poll_election IS NULL OR candidate_election IS NULL OR poll_election <> candidate_election THEN
        RAISE EXCEPTION 'El candidato % no pertenece a la elección de la encuesta %', NEW.candidate_id, NEW.poll_id;
    END IF;

    RETURN NEW;
END $$;

CREATE TRIGGER trg_poll_result_election
BEFORE INSERT OR UPDATE OF poll_id, candidate_id ON poll_result
FOR EACH ROW
EXECUTE FUNCTION validate_poll_result_election();

-- La suma de resultados se valida de forma diferida para permitir reemplazos atómicos dentro de una transacción.
CREATE OR REPLACE FUNCTION validate_poll_result_total()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    affected_poll BIGINT;
    poll_status VARCHAR(20);
    percentage_total DOUBLE PRECISION;
BEGIN
    FOR affected_poll IN
        SELECT DISTINCT poll_id
        FROM (VALUES
            (CASE WHEN TG_OP <> 'DELETE' THEN NEW.poll_id ELSE NULL END),
            (CASE WHEN TG_OP <> 'INSERT' THEN OLD.poll_id ELSE NULL END)
        ) AS affected(poll_id)
        WHERE poll_id IS NOT NULL
    LOOP
        SELECT p.status, coalesce(sum(pr.percentage), 0)
        INTO poll_status, percentage_total
        FROM poll p
        LEFT JOIN poll_result pr ON pr.poll_id = p.id
        WHERE p.id = affected_poll
        GROUP BY p.status;

        IF percentage_total > 100.01 THEN
            RAISE EXCEPTION 'La suma de resultados de la encuesta % supera 100%%', affected_poll;
        END IF;

        IF poll_status = 'APROBADA' AND percentage_total <= 0 THEN
            RAISE EXCEPTION 'La encuesta aprobada % debe tener resultados positivos', affected_poll;
        END IF;
    END LOOP;

    RETURN NULL;
END $$;

CREATE CONSTRAINT TRIGGER trg_poll_result_total
AFTER INSERT OR UPDATE OR DELETE ON poll_result
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION validate_poll_result_total();

CREATE OR REPLACE FUNCTION validate_approved_poll_has_results()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    percentage_total DOUBLE PRECISION;
BEGIN
    IF NEW.status = 'APROBADA' THEN
        SELECT coalesce(sum(pr.percentage), 0)
        INTO percentage_total
        FROM poll_result pr
        WHERE pr.poll_id = NEW.id;

        IF percentage_total <= 0 OR percentage_total > 100.01 THEN
            RAISE EXCEPTION 'La encuesta aprobada % debe tener resultados positivos y no superar 100%%', NEW.id;
        END IF;
    END IF;

    RETURN NEW;
END $$;

CREATE CONSTRAINT TRIGGER trg_approved_poll_results
AFTER INSERT OR UPDATE OF status ON poll
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION validate_approved_poll_has_results();
