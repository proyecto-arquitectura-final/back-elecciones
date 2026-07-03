-- Fortalece la integridad de la gestión de elecciones.
-- No elimina información. La migración se detiene si encuentra datos incompletos o duplicados.

UPDATE election
SET name = btrim(regexp_replace(name, '\s+', ' ', 'g'))
WHERE name IS NOT NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM election
        WHERE name IS NULL
           OR btrim(name) = ''
           OR type IS NULL
           OR round IS NULL
           OR election_date IS NULL
           OR state IS NULL
    ) THEN
        RAISE EXCEPTION 'Existen elecciones incompletas. Corrige nombre, tipo, ronda, fecha y estado antes de aplicar V6';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM election
        GROUP BY lower(btrim(name)), type, round, election_date
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Existen elecciones duplicadas por nombre, tipo, ronda y fecha. Deben depurarse antes de aplicar V6';
    END IF;
END $$;

ALTER TABLE election
    ALTER COLUMN name SET NOT NULL,
    ALTER COLUMN type SET NOT NULL,
    ALTER COLUMN round SET NOT NULL,
    ALTER COLUMN election_date SET NOT NULL,
    ALTER COLUMN state SET NOT NULL;

CREATE UNIQUE INDEX uq_election_definition
    ON election(lower(btrim(name)), type, round, election_date);

CREATE INDEX idx_election_state_date
    ON election(state, election_date DESC);

CREATE INDEX idx_election_type_date
    ON election(type, election_date DESC);
