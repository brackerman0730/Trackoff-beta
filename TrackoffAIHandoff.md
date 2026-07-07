# Trackoff — Project Handoff Prompt

You are picking up development of **Trackoff**, a Java/JavaFX desktop
application originally called "Rankify." The user is a solo developer on
Windows building this for personal use. I will attach the full source
tree; read every file before proposing changes.

---

## 🎯 What Trackoff is

A desktop app for managing and evaluating Spotify playlists. Current
features (already built and working):

1. **Pairwise ranker** — adaptive merge sort with transitivity caching.
   User compares two songs at a time; algorithm asks ~N·log(N) questions
   and produces a final ranked list. Undo up to 200 steps, save/resume.
2. **Tier list builder** — drag-and-drop tiles (album art + gradient +
   title) into S/A/B/C/D/F rows. Auto-tier by ranked percentiles.
   Insertion-index drop (not always-append). Export to CSV or PNG.
3. **Swipe view** — Tinder-style keep/delete for songs. Auto-plays
   30-second Spotify previews. Keyboard shortcuts (←/→/Z). Summary
   screen with CSV export.
4. **Two input sources:** CSV (multiple header formats via alias table)
   and Spotify Web API (Client Credentials flow, hand-rolled JSON parse).

---

## 🖥️ Environment (Windows)

- Windows 10/11, project path: `C:\Users\ackermanb2\Desktop\Personal Projects\Spotify Playlist Rank Decider`
- **Java 25**, **JavaFX SDK at `C:\javafx-sdk-26.0.1\lib`**
- **No Maven** — the user could not get it working. Build system is two
  PowerShell scripts: `compile.ps1` and `run.ps1`. They call `javac`
  and `java` directly with `--module-path` and
  `--add-modules javafx.controls,javafx.media,java.desktop`.
- `-Djava.net.useSystemProxies=true` is set in run.ps1
- Spotify credentials stored plaintext at `%USERPROFILE%\.rankify\spotify.txt`
  (note: still uses `.rankify` folder name — rename to `.trackoff` is
  pending, low priority)
- User may be on a corporate proxy at work (has hit SSL errors on
  Spotify API from work — assume it works at home)

---

## 📁 Existing code structure
src/com/rankify/
├── model/
│   ├── Song.java              (record-like class w/ builder;
│   │                           has id, title, artist, album,
│   │                           imageUrl, previewUrl, popularity)
│   └── Playlist.java
├── io/
│   ├── PlaylistSource.java    (interface)
│   ├── CsvPlaylistSource.java (header-alias based)
│   ├── SpotifyPlaylistSource.java (Client Credentials, hand-rolled JSON)
│   ├── ProgressStore.java     (.rkfy save format)
│   └── RankingCsvWriter.java
├── ranking/
│   ├── AdaptiveMergeSortRanker.java  (with undo stack)
│   ├── TransitivityCache.java        (deep-copy for undo snapshots)
│   ├── ComparisonRequest.java
│   └── ComparisonChoice.java   (LEFT/RIGHT/REMOVE_L/REMOVE_R/REMOVE_BOTH/SKIP_TIE)
└── ui/
├── Main.java
├── MainView.java
├── ComparisonView.java   (album art + preview button)
├── ResultView.java
├── TierListView.java     (drop-index insertion)
├── SwipeView.java
├── Theme.java
└── styles.css            (Spotify dark: bg #121212, accent #1db954)
**Package name is still `com.rankify.*`** — user chose not to rename
the package (only user-facing strings). Keep this in mind.

---

## 🏗️ What we're building next: full playlist manager

Split into 3 phases. Do them in order. Don't skip ahead.

### Phase 1 — Foundation (do this first, as one commit)

1. **Spotify OAuth Authorization Code flow**
   - Replaces (or supplements) current Client Credentials flow
   - Local HTTP server on `localhost:<random_port>` for redirect
   - Refresh token stored at `%USERPROFILE%\.trackoff\spotify_oauth.json`
   - Required scopes at minimum:
     `playlist-read-private playlist-read-collaborative playlist-modify-public playlist-modify-private user-library-read`
   - Must work alongside existing Client Credentials flow for public
     playlists (some users won't want to OAuth)

2. **SQLite persistence layer**
   - Use `sqlite-jdbc` (Xerial). Drop the JAR into `lib\` next to javafx.
   - Update `compile.ps1` and `run.ps1` to include `lib\sqlite-jdbc-*.jar`
     on the classpath.
   - DB file at `%USERPROFILE%\.trackoff\trackoff.db`
   - One `Database.java` helper class. Simple migrations
     (run version-numbered SQL scripts from resources on startup).
   - Initial schema: playlists, songs, playlist_songs (order-preserving),
     playlist_snapshots (for detecting deleted songs), lastfm_scrobbles,
     lastfm_track_stats, song_blocks, block_songs, oauth_tokens

3. **Last.fm account linking**
   - Just API key + username → stored in SQLite (`settings` table)
   - No OAuth needed for read-only Last.fm access, only API key
   - Get user's total scrobble count for progress bar
   - Don't sync scrobbles yet — that's Phase 2

4. **New "My Library" screen**
   - Lists all user's Spotify playlists (from OAuth `/me/playlists`)
   - Shows name, cover art, track count
   - Click a playlist → opens (blank for now — Phase 2 fills it in)

**Phase 1 deliverable:** User can log in to Spotify (OAuth) and
Last.fm, sees their playlists listed. Nothing edits anything yet.

### Phase 2 — Manager UI

- Playlist editor screen with drag-to-reorder, multi-select, delete,
  in-app edits
- **Play count column** from Last.fm, color-coded gray→green by intensity
- **`added_at` timestamps** from Spotify shown as a column
- **Full Last.fm scrobble sync** (~100k scrobbles, first run takes minutes)
  Store in SQLite. Progress bar.
- **Deleted songs section** — snapshot playlist on every load, diff
  against last snapshot, populate deletions table
- **Push changes back to Spotify** via OAuth PUT/DELETE endpoints

### Phase 3 — Smart sorting

- **Preset sort modes** (no sliders yet; that's a future feature):
  Chronological (release date), Album-respecting (group by album,
  respect album track order), BPM ramp (ascending / peak / cooldown),
  Genre-grouped, Play-count descending, Recently-added first
- **Persistent song blocks** — user creates named groups of songs;
  blocks move as a unit; stored in SQLite; visible in sidebar
- **BPM data from AcousticBrainz** (`https://acousticbrainz.org/api/v1/<mbid>/low-level`),
  cached in SQLite. Requires MusicBrainz ID lookup by artist+title —
  hit `musicbrainz.org/ws/2/recording` first, cache MBIDs.
- **Genre data from Spotify** `/artists` endpoint (artist-level genre;
  song-level doesn't exist reliably).

---

## 🎨 Style & conventions (match existing code)

- **File comments:** Every class starts with a Javadoc block explaining
  its purpose in a **"humanlike"** conversational tone (not corporate
  Javadoc-speak). Look at `TierListView.java`'s top comment for tone.
- **Method comments:** Inline `// like this` for anything non-obvious.
  Especially explain *why*, not *what*.
- **Structure:** Section dividers like:
  ```java
  // ==================================================================
  //  Section name
  // ==================================================================
  Prefer standalone files over huge monolithic classes. Split UI screens into their own view classes (see ComparisonView, TierListView, SwipeView).
Zero third-party deps except SQLite JDBC and JavaFX. Do NOT pull in Jackson, Gson, OkHttp, etc. Hand-roll HTTP and JSON like the existing SpotifyPlaylistSource does. User has strong preference for minimal dependencies.
No Maven. Everything is compiled with javac via PowerShell. If you propose Maven or Gradle, the user will reject it.
Spotify green: #1db954 primary, #1ed760 hover.
Pill buttons. Cards use #282828 on #121212 background.
Undo where reasonable — user likes the "safety net" pattern (see AdaptiveMergeSortRanker's snapshot-based undo).
🐛 Known gotchas / decisions to preserve
FlowPane.prefWrapLength must be bound to widthProperty(), not set to a literal value, or row heights explode.
Tier list drop-index calculation exists in TierListView.computeDropIndex(). Don't regress this back to always-append behavior.
MediaPlayer for previews must be .stop() + .dispose()'d when navigating away, or audio bleeds between views.
CSV parser handles multiple header formats via alias table — see CsvPlaylistSource. Extend that table for new CSV formats, don't hard-code column indices.
Save-file format is .rkfy and treats the transitivity cache as opaque (empty edge list on save). Resume may re-ask some inferred comparisons but never produces wrong results.
User is on Windows — use Path/Paths APIs, never string-concat file paths with /. %USERPROFILE% is System.getProperty("user.home").
🚦 How to work
Read the whole codebase first. Don't propose changes without reading at least MainView.java, SpotifyPlaylistSource.java, and Song.java — those set the patterns.
Ask clarifying questions before writing code for anything ambiguous. User strongly prefers a short Q&A round over a wrong implementation.
Present a file-by-file plan before generating code for a new phase. User will approve/adjust the plan first.
When generating code, generate whole files at a time (not diffs), since the user pastes into their editor.
Never use Windows batch files (.bat/.cmd). PowerShell only. javac @argfile has a known bug with backslash-escaped spaces on Windows — the PowerShell scripts avoid it.
Test compile-ability by reading before shipping. The user's feedback loop is: paste code → run .\compile.ps1 → see if it builds. Don't ship code with obvious errors (references to variables not in scope, missing imports, etc.). This has happened twice — once with a lambda referencing playlist before load, once with setPrefWrapLength(1).
✅ Decisions already made (don't re-litigate)
Java 25 + JavaFX 26 (SDK), no build system. Final.
No Maven / Gradle. Final.
SQLite for persistence, not JSON files or H2. Final.
AcousticBrainz for BPM, not Spotify audio-features (audio-features endpoint blocked for new apps as of Nov 2024).
Preset sort modes only for Phase 3 — no slider UI yet.
Persistent named blocks — not just multi-select.
Deleted songs = "deleted while Trackoff was watching." User accepts this limitation (Spotify API doesn't expose deletion history).
Name is Trackoff. Rebranding from "Rankify" is cosmetic-only (window titles + main label). Package com.rankify.* stays.
Legal check done: TRACKOFF trademark (privacy software, Class 009) was cancelled by USPTO in July 2024, so naming clash is minimal.
📌 Your first message back to the user
If you're picking this up fresh, respond with:

Confirmation you've read all files
A file-by-file plan for Phase 1 only:
Which new files you'll create
Which existing files you'll modify
What the SQLite schema looks like
What the OAuth flow looks like at high level
Any clarifying questions before you touch code
Do NOT start generating code until the user approves the plan.
---

## Notes on using this prompt

- Save the `.md` file into your project root next to `compile.ps1`
- When you switch models/computers, upload the entire `src\` folder plus this file plus `compile.ps1` and `run.ps1`
- If your new AI has smaller context windows, ask it to summarize the codebase back to you first — that'll surface any files it couldn't fit
- The "decisions already made" section is the single most important part — it prevents a new AI from suggesting Maven or Gson for the tenth time

