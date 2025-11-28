CREATE TABLE rule_evaluations
(
    id            BIGSERIAL PRIMARY KEY,
    assessment_id UUID         NOT NULL,
    rule_name     VARCHAR(100) NOT NULL,
    rule_type     VARCHAR(20)  NOT NULL,
    score_impact  INTEGER      NOT NULL,
    description   TEXT,
    CONSTRAINT fk_assessment FOREIGN KEY (assessment_id)
        REFERENCES risk_assessments (id) ON DELETE CASCADE
);

CREATE INDEX idx_assessment_rules ON rule_evaluations (assessment_id);
