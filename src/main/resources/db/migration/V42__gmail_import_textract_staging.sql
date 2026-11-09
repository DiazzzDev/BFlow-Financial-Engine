ALTER TABLE gmail_imports
    ADD COLUMN IF NOT EXISTS s3_object_key VARCHAR(512);

ALTER TABLE gmail_imports
    ADD COLUMN IF NOT EXISTS textract_job_id VARCHAR(128);

CREATE UNIQUE INDEX IF NOT EXISTS uq_gmail_import_textract_job
    ON gmail_imports(textract_job_id)
    WHERE textract_job_id IS NOT NULL;
