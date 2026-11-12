# API samples

Captured on 2026-10-16 (Worlds 2026 Play-Ins). Used as test fixtures.

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
| getStandings_lck.json | `getStandings?hl=en-US&tournamentId=115548147890329817` (populated rankings) |
| getStandings_worlds2026.json | `getStandings?hl=en-US&tournamentId=115660540725177488` (empty rankings: bracket/swiss stages) |
| getStandings_worlds2026_knockouts.json | Same request, captured 2026-11-11 with Knockouts played up to the Final (7 matches: 4 + 2 + 1, Final unstarted); `previousMatchIds` is still empty, so the bracket comes from match order |
| getStandings_msi2026.json | `getStandings?hl=en-US&tournamentId=115570934354631452` captured 2026-11-12 (MSI 2026, fully played): two double-elimination sections, "Play In Knockouts" (4 teams, 6 matches, not listed in play order) and "Knockouts" (8 teams, 14 matches) |
| getEventDetails.json | `getEventDetails?hl=en-US&id=115660588583040710` (MVK vs FUR, Bo5; game ids here) |
| getTeams_t1.json | `getTeams?hl=en-US&id=t1` captured 2026-11-11 (T1, 14 players; `id` also accepts the team id `98767991853197861`). Never call `getTeams` without `id`: it returns every team (~1.5 MB) |

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
