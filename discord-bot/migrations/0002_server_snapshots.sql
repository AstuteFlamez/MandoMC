CREATE TABLE server_snapshots (
  server TEXT PRIMARY KEY CHECK(server IN ('production','development')),
  body TEXT NOT NULL,
  updated INTEGER NOT NULL
);
INSERT INTO server_snapshots(server,body,updated)
SELECT 'production',body,updated FROM snapshots WHERE id=1;
