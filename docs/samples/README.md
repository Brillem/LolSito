# API samples

Captured on 2026-10-16 (Worlds 2026 Play-Ins) unless a row says otherwise. Used as test fixtures (`app/src/test/resources/fixtures/`).

## Gateway (requires header)
Base: `https://esports-api.lolesports.com/persisted/gw`
Header: `x-api-key: 0TvQnueqKa5mxJntVWt0w4LpLfEkrV1Ta8rQBb9Z` (public web key; 403 without it)

| File | Request |
|------|---------|
| getLeagues.json | `getLeagues?hl=en-US` |
| getSchedule_worlds.json | `getSchedule?hl=en-US&leagueId=98767975604431411` (pagination: `pageToken` from `pages.older/newer`) |
| getLive.json | `getLive?hl=en-US` (empty: no live match at capture time) |
| getLive_live.json | `getLive?hl=en-US` captured 2026-11-11 with one live match (LCP P&R, FAK vs GZ, Bo5, 2-2); `type` is `match` here, other values (shows) have no `match` |
| getTournamentsForLeague_worlds.json | `getTournamentsForLeague?hl=en-US&leagueId=98767975604431411` |
| getStandingsV3_worlds2026.json | `getStandingsV3?hl=en-US&tournamentId=115660540725177488` captured 2026-11-12 (Worlds 2026 up to the Final, which is unstarted): Play-Ins double elimination, Swiss in 5 round columns, Knockouts 4 + 2 + 1 |
| getStandingsV3_lck.json | `getStandingsV3?hl=en-US&tournamentId=115548147890329817` captured 2026-11-12 (LCK 2026 Split 3, fully played): two ranked groups (no matches), Play-Ins (3 matches) and the Regional Championship gauntlet (7 columns, 10 matches, some slots fed by decision points) |
| getStandingsV3_lec.json | `getStandingsV3?hl=en-US&tournamentId=115548681802226458` captured 2026-11-12 (LEC 2026 Split 3, fully played): regular season table and a 6-team double-elimination Playoffs (5 columns, 8 matches, seeds from `seeding` origins) |
| getStandingsV3_msi2026.json | `getStandingsV3?hl=en-US&tournamentId=115570934354631452` captured 2026-11-12 (MSI 2026, fully played): two double-elimination sections, "Play In Knockouts" (4 teams, 6 matches) and "Knockouts" (8 teams, 14 matches) |
| getEventDetails.json | `getEventDetails?hl=en-US&id=115660588583040710` (MVK vs FUR, Bo5; game ids here) |
| getTeams_t1.json | `getTeams?hl=en-US&id=t1` captured 2026-11-11 (T1, 14 players; `id` also accepts the team id `98767991853197861`). Never call `getTeams` without `id`: it returns every team (~1.5 MB) |

### getStandingsV3
- `standings[].stages[].sections[]` has a `type`: `group` (a table in `rankings`, no matches), `bracket` (matches in
  `columns[].cells[].matches[]`, no `rankings`) or `crossGroup` (rankings, seen in LCK Cup 2025). Swiss stages are
  `bracket` sections with one column per round. There is no flat match list per section.
- Each cell has a `name` such as "Upper Bracket - Round 2" or "Lower Bracket - Finals"; a column may hold several
  cells (upper above lower).
- Each team of a match has an `origin`: `{"type": "match", "structuralId": <another match's structuralId>, "slot": n}`
  or a `decisionPoint` / `seeding` origin (draw, seeds, earlier stage). For `match` origins, slot 1 is the winner
  and slot 2 the loser of that match: checked against every decided match of 102 tournaments (latest two of every
  league, plus older Worlds, MSI, LCK, LEC, LPL, LCS and CBLOL editions back to 2015) on 2026-11-12 (1084 slots: 749 winners in slot 1, 335 losers in slot 2), with
  no exception. Match origins always point inside the same section.
- Every bracket section of those tournaments came with columns, and their match counts equal what `getStandings`
  listed. No sample had a not-yet-known (TBD) team, so how V3 sends one is not confirmed; `getStandings` used
  `id` "0" and `code` "TBD".

## Livestats (no key)
Base: `https://feed.lolesports.com/livestats/v1`
`startingTime` must be RFC3339 aligned to 10 seconds, otherwise HTTP 400.

| File | Request |
|------|---------|
| livestats_window.json | `window/115660588583040711?startingTime=2026-10-15T23:30:00Z` |
| livestats_details.json | `details/115660588583040711?startingTime=2026-10-15T23:30:00Z` |

Example:
```sh
curl -s -H "x-api-key: 0TvQnueqKa5mxJntVWt0w4LpLfEkrV1Ta8rQBb9Z" \
  "https://esports-api.lolesports.com/persisted/gw/getLive?hl=en-US"
```
