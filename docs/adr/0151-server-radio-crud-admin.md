# ADR 0151 — Server Internet Radio CRUD (Admin)

Date: 2026-11-02  
Status: Accepted  
Related: ADR 0020, ADR 0131, `docs/NAVIDROME-API-CAPABILITIES.md`

## Context

The app already listed Navidrome internet radios via `getInternetRadioStations` and
kept personal bookmarks in local Room (`radio_favorites`, ADR 0020). Users could
not add, edit, or remove stations on the server. Subsonic/Navidrome expose
`createInternetRadioStation`, `updateInternetRadioStation`, and
`deleteInternetRadioStation` (API ≥ 1.16.0). Mutations are **admin-only** and
change a **global** station list for every client.

## Decision

1. **Approach A only** — wire server CRUD; do not invent local-only custom stations.
2. Gate mutate UI on `getUser` → `adminRole`. Non-admins still browse + bookmark.
3. Add Retrofit methods for create / update / delete; map Subsonic error **50** to
   a clear “Admin privileges required…” message.
4. After delete, remove any local bookmark for that `station_id` (orphan cleanup).
5. Keep bookmark semantics local-only (ADR 0020). Bookmark remove ≠ server delete.

## Consequences

- Self-hosted admins can manage the shared catalog from Library → Radio.
- Non-admin accounts never see Add / Edit / Delete; API failures still surface if
  someone calls the ViewModel methods directly.
- Older Navidrome builds that lacked admin middleware may have allowed non-admin
  mutations; clients still hide UI unless `adminRole` is true.
