-- Drops decorative flags that were only ever written from the
-- request DTO to the entity and echoed back in the response, never
-- actually read for any business logic, report, or calculation.
-- Frontend was required to send them on every create/update even
-- though nothing downstream depended on their value.
ALTER TABLE expenses DROP COLUMN tax_deductible;
ALTER TABLE expenses DROP COLUMN reimbursable;
ALTER TABLE incomes DROP COLUMN taxable;
