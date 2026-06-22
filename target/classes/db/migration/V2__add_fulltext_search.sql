-- Add full-text search vector column for better similarity matching
ALTER TABLE engineering_document ADD COLUMN IF NOT EXISTS search_vector tsvector;

-- Generate tsvector from searchable_text and summary
UPDATE engineering_document
SET search_vector = to_tsvector('english', coalesce(summary, '') || ' ' || coalesce(searchable_text, ''));

-- Create GIN index for fast full-text search
CREATE INDEX IF NOT EXISTS idx_doc_search_vector ON engineering_document USING gin(search_vector);

-- Create trigger to auto-update search_vector on insert/update
CREATE OR REPLACE FUNCTION update_search_vector() RETURNS trigger AS $$
BEGIN
    NEW.search_vector := to_tsvector('english', coalesce(NEW.summary, '') || ' ' || coalesce(NEW.searchable_text, ''));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_update_search_vector ON engineering_document;
CREATE TRIGGER trg_update_search_vector
    BEFORE INSERT OR UPDATE ON engineering_document
    FOR EACH ROW EXECUTE FUNCTION update_search_vector();
