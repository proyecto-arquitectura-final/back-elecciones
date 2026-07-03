-- Vincula cada candidato con una elección concreta.
-- No elimina datos ni modifica migraciones anteriores.

ALTER TABLE candidate
    ADD COLUMN election_id BIGINT;

UPDATE candidate c
SET election_id = (
    SELECT e.id
    FROM election e
    WHERE e.type = c.election_type
    ORDER BY
        CASE e.state
            WHEN 'EN_CONTEO' THEN 1
            WHEN 'ABIERTA' THEN 2
            WHEN 'CONFIGURADA' THEN 3
            WHEN 'CERRADA' THEN 4
            ELSE 5
        END,
        e.election_date DESC NULLS LAST,
        e.id DESC
    LIMIT 1
);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM candidate WHERE election_id IS NULL) THEN
        RAISE EXCEPTION 'No fue posible asociar todos los candidatos con una elección del mismo tipo';
    END IF;
END $$;

ALTER TABLE candidate
    ALTER COLUMN election_id SET NOT NULL;

ALTER TABLE candidate
    ADD CONSTRAINT candidate_election_fk
        FOREIGN KEY (election_id) REFERENCES election(id);

CREATE INDEX idx_candidate_election_id
    ON candidate(election_id);

CREATE UNIQUE INDEX uq_candidate_election_normalized_name
    ON candidate(election_id, lower(btrim(name)));
