# Analytics Strategy

> Analysis / planning document. No code changes are implied by this file existing — it's the shared
> reasoning behind how we measure the app, so Android and iOS stay aligned. A twin copy lives in the
> iOS repo.

## 0. Platform note (Android)

On Android the system is **`TTSStatsRepository`**
(`.../data/repository/TTSStatsRepository.kt`), backed by `SharedPreferences`. The shared key grammar is
`QuizStat` / `PageStat` in `.../data/stats/QuizStat.kt` (with `recordQuizCompletion` /
`recordPageView` extension functions). Platform segment in keys is `and`. The iOS twin is `StatsManager`
(backed by `UserDefaults`), platform segment `ios`. Keep the two in step — same metric names, same areas,
same meaning. (The repository name is historical: it began as a Google TTS API-call counter and grew into
the general stats store.)

---

## 1. Purpose

Two concrete aims drive this:

1. **Assess the success of areas of the app** — e.g. notice when a screen such as *Pronouns*
   (*Pronomen* in the DE flavour) becomes unusually popular.
2. **Classify users from their own data** — who is a committed user (and a likely payer), and who is
   drifting toward churn.

Plus the standard mobile-analytics practices that make those answers trustworthy over time.

---

## 2. What the system is

A **local-buffer counter store, flushed daily to Firestore**, with snapshot → upload →
subtract-on-success (so counts are never double-counted and never lost on a failed write). Counters land
in two destinations:

- **Global monthly doc** `stats/YYYY-MM` — aggregate across all users, month-over-month trend built in.
- **Per-user doc** `users/<uid>` — accumulates over the user's lifetime.

Key grammar already in place and reconcilable 1:1 to a sheet/quiz:

- Quizzes: `q_<platform>_<area>_<sheetId>_<metric>` with metrics `runs` / `ok` / `notok` / `total`
  (so `ok + notok == total`, `runs` = completions).
- Page popularity: `p_<platform>_<area>_<sheetId>_<metric>` with metrics `open` / `tap`.

Language is implied by the per-flavour Firebase database, so it is **not** in the key.

This is the right design for **tallies we control and must reconcile exactly**. Keep it for that.

---

## 3. What we already have that is strong

- **Recency** — every user doc carries `lastActivityDate`. Sorting it descending already gives the
  recently-active cohort. This is the single most predictive churn signal, and we have it.
- **Distinct active days** — `activityDays` is an array to which a unique date string is appended per
  day of use. 20 entries ⇒ the user has opened the app on 20 separate days. This is a per-user
  engagement/loyalty measure most apps lack, and it yields several derived metrics for free:
  - **tenure** = first entry in `activityDays` (a natural cohort tag),
  - **lifetime active days** = array length,
  - **active days per month** = count of entries in a given month,
  - **recency** = latest entry (cross-checks `lastActivityDate`),
  - **streaks / gaps** = run-length over the sorted dates.
- **Cost & quota governance (global stats)** — the per-month-billed APIs are tracked so we can watch
  free-vs-paid levels and spend: Google TTS (`TTSCallCount`, `TTSChars`, `TTSEstCost`, `TTSQuality`,
  plus the per-charging-level char splits) and paid AI calls (`deepSeekCallCount`,
  `deepSeekEstCostUSD`, and the OpenAI/Gemini equivalents). The monthly global doc is exactly the right
  grain for a monthly bill.
- **Operational health** — serious errors roll up under `faultCount` (plus a named counter per fault
  category), so a spike is visible.
- **Quiz success** — `q_*` `ok`/`notok`/`total`/`runs` let us estimate which quizzes land and which
  frustrate, per sheet.

---

## 4. The real gaps (now small)

Because recency and active-days are already covered, only three gaps remain:

1. **Distinct-user reach at the global level.** A counter in `stats/YYYY-MM` sums *events*, not people —
   it cannot tell "many users once" from "few users often." Reach needs either per-user aggregation
   (offline) or a tool that dedupes users automatically (see §7).
2. **Per-user feature trend over time.** The user doc holds *lifetime* feature counters, so we can't
   see *when* a user's Pronomen use happened or whether their quiz activity is rising or falling.
   `activityDays` gives time-presence but not per-feature-per-period volume.
3. **Cohort retention curves.** Derivable in principle from `activityDays[0]` as the cohort tag, but
   awkward to compute from Firestore docs by hand; far cheaper from an analytics export (§7).

None of these require abandoning the counter model — they're additive, or delegated to a tool built for
them.

---

## 5. Aim 1 — "Is Pronomen unusually popular?"

We already emit `p_<plat>_reference_GermanPronouns_open` / `_tap`. Do **not** read raw opens — they grow
with the user base, so everything looks like it's "growing." Three cheap derived views instead:

- **Share of attention** — `GermanPronouns_open ÷ Σ(all reference _open)` for the month. Removes overall
  growth; a rising *share* is genuine popularity.
- **Engagement quality** — `tap ÷ open` per sheet. High tap/open = real interest; low = people landing
  there by accident.
- **Reach vs frequency** — opens alone conflate breadth and intensity. Reach (distinct users) comes from
  §7; frequency (opens per active user) becomes available once we have an active-user denominator (which
  `activityDays`/`lastActivityDate` can supply by counting users active in the month).

**Alarm**: a small monthly job (or one BigQuery query after export) that ranks sheets by share-of-opens
and flags any sheet whose share jumped more than X% over its trailing 3-month average.

---

## 6. Aim 2 — User quality, propensity to pay, churn risk

The standard frame is **RFM** (Recency, Frequency, Monetary). We can do all three **today**:

- **Recency** — `lastActivityDate` (and latest `activityDays` entry).
- **Frequency** — `activityDays` length and active-days-per-month; plus depth counters (`mp3PlayedCount`,
  quiz `runs`, `statProgress<exam>Completed/Total`).
- **Monetary / intent** — the IAP counters we already collect: `statIAPSheetDisplayedCount`,
  `statIAPHourlyHitCount` / `statIAPDailyHitCount`, `statIAPBuyCancelledCount`, `statIAPBoughtCount`.

**Strongest buy signal we have:** `statRateLimiterForbidCount` (and the hour/day variants). A user who
repeatedly hits the free cap is demand meeting a wall — engaged *and* blocked is the prime upgrade
candidate.

**Breadth** (count of distinct `p_*` / `q_*` keys a user has touched) correlates strongly with retention;
worth computing as a feature in any score.

### A simple, explainable scoring model (no ML to start)

- **Committed / likely to stay** — breadth ≥ N areas **and** `lastActivityDate` within ~7–14 days **and**
  some quiz completions or measurable progress.
- **Likely to pay** — rate-limiter forbids ≥ k **or** IAP sheet shown ≥ k, **combined with** high
  mp3/quiz volume (engaged *and* hitting the wall).
- **Churn risk** — non-trivial lifetime activity **but** `lastActivityDate` now older than ~21–30 days,
  **or** never reached the activation moment (§7).

### Acting on it

We already use **Remote Config** — time the paywall to the moment a high-engagement user hits the cap,
rather than showing it blanket. Re-engage "churn risk" users (notification / Word-of-the-Day hook) before
the gap widens.

---

## 7. The standard mobile-analytics playbook to adopt

- **One North Star metric** — a weekly learning-outcome proxy everything is steered toward, e.g. *weekly
  quizzes completed* or *weekly minutes of audio played*. Everything else is diagnostic.
- **Activation / "aha moment"** — the earliest action that predicts retention (candidate: *completed
  first quiz*, or *played ≥ N sentences in the first session*). Track the % of new users who reach it in
  their first day; raising it moves retention more than almost any feature.
- **AARRR skeleton** — Acquisition → **Activation** → **Retention** → **Revenue** → Referral. We're
  strong on Revenue signals and feature tallies, weak on Acquisition source and Retention curves — the
  exact gap a dedicated tool fills.
- **Retention** — D1/D7/D30 and weekly cohort curves (cohort = `activityDays[0]`).
- **Engagement** — DAU/WAU/MAU and **stickiness = DAU/MAU**. `activityDays` already gives a per-user
  active-days basis for this.
- **Feature adoption** — per area: reach% and frequency, tracked over time.
- **Monetization funnel** — paywall shown → cancelled → bought, and free-limit-hit → upgrade rate. We
  already have every counter for this.

---

## 8. Build vs. buy — use both, for different jobs

**Keep the counter system** for:
- cost / quota governance (TTS + paid-AI spend against monthly billing),
- operational health (`faultCount`),
- exact, reconcilable feature tallies (quiz `ok`/`notok`, page open/tap).

It is the right tool for anything we must reconcile to the cent or to a specific sheet.

**Add Firebase Analytics (GA4)** for the things counters fundamentally cannot do:
- automatic **sessions, DAU/MAU, retention cohorts, funnels, distinct-user reach, acquisition source**.
- Dual-write the area/sheet taxonomy we already define (`reference/GermanPronouns`, …) as GA4 events —
  cheap, because our key grammar already *is* the taxonomy.
- **Export GA4 → BigQuery** for SQL cohort/segmentation work and to join against the Firestore counters.

Already in the Firebase box we own:
- **Crashlytics** for the `faultCount` family — a real crash/fault tool beats a counter for this.
- **Remote Config + A/B testing** — ship a feature behind a flag, read success in GA4. Closes the loop:
  measure *and* act.

**Hygiene:** lifetime-cumulative per-user counters bloat the user doc. If per-user feature *trend*
becomes important, a per-user monthly rollup doc (`users/<uid>/months/YYYY-MM`, doc id chosen by month at
flush time) fits the existing flush model and keeps docs small — but it's lower priority now that
`activityDays` + `lastActivityDate` already cover recency and frequency.

---

## 9. Suggested order of work

1. **GA4 dual-write** of the existing area/sheet taxonomy → retention, funnels, DAU/MAU, distinct-user
   reach, acquisition — near-zero new code, removes the biggest blind spot (reach).
2. **Define North Star + activation moment**, then a rules-based user score (committed / likely-to-pay /
   at-risk) from counters + `lastActivityDate` + `activityDays` we already have.
3. **Share-of-attention + tap/open monthly ranking** with a jump alarm for Aim 1.
4. **Act**: Remote-Config-timed paywall for "likely to pay"; re-engagement for "at-risk"; A/B test
   changes and read results in GA4.
5. *(Optional, later)* per-user monthly rollup for per-feature trend and cleaner cohort feature-adoption.

Item 1 is the quick win: it supplies reach, retention and funnels — the things the counter store was
never meant to produce — while the counter store keeps doing cost, faults and exact tallies, and
`lastActivityDate` + `activityDays` already carry recency and frequency.
