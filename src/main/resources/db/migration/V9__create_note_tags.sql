CREATE TABLE note_tags (
    note_id UUID NOT NULL REFERENCES notes (id) ON DELETE CASCADE,
    tag_id  UUID NOT NULL REFERENCES tags  (id) ON DELETE CASCADE,
    PRIMARY KEY (note_id, tag_id)
);

CREATE INDEX idx_note_tags_tag_id ON note_tags (tag_id);
