CREATE TABLE links (
  uuid TEXT PRIMARY KEY,
  discord_id TEXT NOT NULL UNIQUE,
  username TEXT NOT NULL,
  state TEXT
);
CREATE TABLE codes (
  uuid TEXT PRIMARY KEY,
  username TEXT NOT NULL,
  code_hash TEXT NOT NULL UNIQUE,
  expires INTEGER NOT NULL
);
CREATE INDEX codes_expiry ON codes(expires);
CREATE TABLE attempts (discord_id TEXT PRIMARY KEY, window INTEGER NOT NULL, count INTEGER NOT NULL);
CREATE TABLE snapshots (id INTEGER PRIMARY KEY CHECK (id = 1), body TEXT NOT NULL, updated INTEGER NOT NULL);
CREATE TABLE leases (id TEXT PRIMARY KEY, owner TEXT NOT NULL, expires INTEGER NOT NULL);
CREATE TABLE tickets (
  id TEXT PRIMARY KEY,
  discord_id TEXT NOT NULL,
  channel_id TEXT UNIQUE,
  category TEXT NOT NULL,
  status TEXT NOT NULL CHECK(status IN ('creating','open','closing','closed')),
  claimed_by TEXT
);
CREATE UNIQUE INDEX one_open_ticket ON tickets(discord_id) WHERE status != 'closed';
