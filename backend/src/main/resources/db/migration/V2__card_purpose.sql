-- v0.0.19 🍊 Question/approval cards carry a purpose (who handles the answer) and a JSON payload.
ALTER TABLE question_card
    ADD COLUMN purpose VARCHAR(32) NOT NULL DEFAULT 'QUESTION' AFTER kind,
    ADD COLUMN payload JSON NULL AFTER allow_other;
