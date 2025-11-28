CREATE TABLE risk_assessments
(
    id                 UUID PRIMARY KEY,
    transaction_id     UUID                     NOT NULL UNIQUE,
    risk_score_value   INTEGER                  NOT NULL CHECK (risk_score_value >= 0 AND risk_score_value <= 100),
    risk_level         VARCHAR(20)              NOT NULL,
    decision           VARCHAR(20)              NOT NULL,
    ml_prediction_json JSONB,
    assessment_time    TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_transaction_id ON risk_assessments (transaction_id);
CREATE INDEX idx_assessment_time ON risk_assessments (assessment_time);
CREATE INDEX idx_risk_level ON risk_assessments (risk_level);
CREATE INDEX idx_decision ON risk_assessments (decision);
