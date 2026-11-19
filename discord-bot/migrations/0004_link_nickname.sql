-- NULL means no nickname write is pending. Existing links keep their current nicknames.
ALTER TABLE links ADD COLUMN nickname_sync_after INTEGER;
