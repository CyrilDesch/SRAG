ALTER TABLE transcripts 
ADD COLUMN words JSONB NOT NULL DEFAULT '[]'::jsonb;

-- Convert existing text to a single word entry with default timestamps
UPDATE transcripts 
SET words = jsonb_build_array(
  jsonb_build_object(
    'text', text,
    'start', 0,
    'end', 0,
    'confidence', confidence
  )
)
WHERE words = '[]'::jsonb AND text IS NOT NULL AND text != '';

CREATE INDEX idx_transcripts_words ON transcripts USING GIN(words);

ALTER TABLE transcripts DROP COLUMN text;

