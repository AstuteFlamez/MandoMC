# M4-ND public gameplay knowledge

M4-ND can answer questions beyond his short written topic notes. The plugin retrieves approved public gameplay documentation, recipe/loot/shop configuration and relevant gameplay source from `AstuteFlamez/MandoMC`, caches it locally and searches for a few relevant passages. The droid's presentation remains provider-free. He explains MandoMC and cannot perform actions.

## Version and trust boundary

`scripts/build.sh` generates `guide-repository.json` into ignored build resources before compilation. It records the exact full source commit, whether the checkout is clean, and a reviewed allowlist of public paths with byte lengths and SHA-256 hashes. The JAR carries this manifest. Modified or unversioned builds retain local guidance and do not fetch repository knowledge.

After a deployment and normal restart, the guide worker loads the cache for that exact embedded commit or downloads its public repository archive. It never looks up the latest branch or falls back to another version's cache. Every approved file must match the embedded hash and length before it is indexed. Production and dev therefore use their own deployed snapshots, even when their branches contain different mechanics. This refresh happens off Paper's tick loop.

GitHub's [repository content API](https://docs.github.com/en/rest/repos/contents) supports unauthenticated public reads and commit references. The archive request has no token. Redirects are restricted to this repository's exact commit on GitHub's archive host. A shared REST-rate limit may use the same commit's fixed public archive URL; it never changes the source version. Downloads, decompression, individual files and cache sizes are bounded. No source files are extracted into the runtime.

Only reviewed gameplay areas in tracked public source/configuration and selected public docs are eligible. Runtime player data, inventories, balances, clan records, credentials, local files, agent instructions, deployment instructions, administration internals and arbitrary URLs are excluded. Downloaded/cached files are treated as untrusted data and validated against the build manifest. The model receives only relevant bounded passages, never the full repository or local cache. Public source examples cannot establish a player's private state. Current verified notes and reviewed common overrides take precedence over generic defaults; proposed/deferred documentation is not an available feature.

## Responses and fallback

Exact acquisition/crafting responses and beginner first steps still use verified local guidance, avoiding invented ingredients or processing steps and spending no AI quota. Open-ended questions combine maintained public topic notes with the best repository passages. Unsupported topics are refused. Existing character, plain-text replies, private delivery, rate limits and budgets remain. The model does not expose repository paths, hashes or hosting details in gameplay answers.

A valid same-version cache works without network access. A missing/corrupt cache, unavailable archive or unknown build retains the existing written guidance. There is no older-version or latest-dev fallback. Repository fallback text and an enable switch live in the paired protocol-droid configuration; there is no setting for an unapproved host or credential.

## Verification

Unit checks cover the public allowlist, source hashes, cache corruption, version separation, offline same-version operation, modified-build refusal, redirect restrictions, bounded downloads, relevant search and off-topic refusal. Native checks exercise real public retrieval on a clean published build, cache validation, private question dialogs, exact coal/first-saber/beginner guidance, real AI answers about homes/repairs/mining/death, the skin NPC and staff-forge inventory restoration. Production remains unchanged unless separately requested.
