ALTER TABLE decision ADD COLUMN overrides_decision_id UUID REFERENCES decision (id);

CREATE INDEX idx_decision_overrides_decision_id ON decision (overrides_decision_id);
