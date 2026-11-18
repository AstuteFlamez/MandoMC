CREATE TABLE ticket_numbers (
  number INTEGER PRIMARY KEY AUTOINCREMENT,
  ticket_id TEXT NOT NULL UNIQUE
);
INSERT INTO ticket_numbers(ticket_id) SELECT id FROM tickets ORDER BY id;
