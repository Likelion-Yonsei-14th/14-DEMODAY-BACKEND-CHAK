-- Keep this migration as the first legacy upgrade step. If duplicate owner rows
-- exist, it fails before any feature columns or tables are added.
ALTER TABLE personal_desks
    ADD CONSTRAINT uk_personal_desks_owner UNIQUE (owner_id);
