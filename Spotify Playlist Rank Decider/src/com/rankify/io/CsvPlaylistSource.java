package com.rankify.io;

import com.rankify.model.Playlist;
import com.rankify.model.Song;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses standard comma-separated CSV exports produced by tools like
 * TuneMyMusic, Exportify, etc. The first row is expected to be a header
 * so we can map columns by name instead of position — this way the parser
 * survives most reordering or extra-column additions from converters.
 *
 * Handles quoted fields containing commas, escaped quotes ("") and
 * BOM-prefixed files.
 */
public final class CsvPlaylistSource implements PlaylistSource {

    @Override
    public Playlist load(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        List<String> lines = Files.readAllLines(path);
        if (lines.isEmpty()) {
            return new Playlist(pathName(path), List.of());
        }

        // Strip UTF-8 BOM if present.
        String headerLine = lines.get(0);
        if (!headerLine.isEmpty() && headerLine.charAt(0) == '\uFEFF') {
            headerLine = headerLine.substring(1);
        }

        List<String> headers = parseCsvLine(headerLine);
        Map<String, Integer> col = new HashMap<>();
        for (int k = 0; k < headers.size(); k++) {
            col.put(headers.get(k).trim(), k);
        }

        List<Song> songs = new ArrayList<>();
        for (int lineNum = 1; lineNum < lines.size(); lineNum++) {
            String raw = lines.get(lineNum);
            if (raw.isBlank()) continue;

            List<String> fields = parseCsvLine(raw);
            Song song = buildSong(fields, col, lineNum);
            if (song != null) songs.add(song);
        }

        return new Playlist(pathName(path), songs);
    }

    // ------------------------------------------------------------------

    private Song buildSong(List<String> fields, Map<String, Integer> col, int lineNum) {
        String title  = get(fields, col, "Song");
        String artist = get(fields, col, "Artist");
        String id     = get(fields, col, "Spotify Track Id");

        // Fall back gracefully — a row with no title is likely a broken export.
        if (title.isEmpty()) return null;
        if (id.isEmpty())    id = "row-" + lineNum;
        if (artist.isEmpty()) artist = "Unknown Artist";

        return Song.builder()
                .id(id)
                .title(title)
                .artist(artist)
                .album(get(fields, col, "Album"))
                .albumDate(get(fields, col, "Album Date"))
                .bpm(parseInt(get(fields, col, "BPM")))
                .popularity(parseInt(get(fields, col, "Popularity")))
                .energy(parseInt(get(fields, col, "Energy")))
                .key(get(fields, col, "Key"))
                .camelot(get(fields, col, "Camelot"))
                .genres(get(fields, col, "Genres"))
                .explicit(get(fields, col, "Explicit").equalsIgnoreCase("yes"))
                .durationSeconds(parseDuration(get(fields, col, "Duration")))
                .build();
    }

    /** Look up a column by header name; returns "" if the column doesn't exist. */
    private String get(List<String> fields, Map<String, Integer> col, String name) {
        Integer idx = col.get(name);
        if (idx == null || idx >= fields.size()) return "";
        return fields.get(idx).trim();
    }

    // ------------------------------------------------------------------
    //  Minimal but correct CSV line parser.
    //  Handles: "quoted, values", escaped quotes (""), and plain fields.
    // ------------------------------------------------------------------
    private List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (inQuotes) {
                if (c == '"') {
                    // Doubled quote inside a quoted field → literal quote.
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else {
                if (c == ',') {
                    out.add(field.toString());
                    field.setLength(0);
                } else if (c == '"' && field.length() == 0) {
                    inQuotes = true;
                } else {
                    field.append(c);
                }
            }
        }
        out.add(field.toString());
        return out;
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s.strip()); }
        catch (NumberFormatException e) { return 0; }
    }

    private static int parseDuration(String s) {
        // "03:28" -> 208 seconds
        String[] parts = s.split(":");
        if (parts.length != 2) return 0;
        return parseInt(parts[0]) * 60 + parseInt(parts[1]);
    }

    private static String pathName(Path p) {
        return p.getFileName().toString().replaceFirst("\\.[^.]+$", "");
    }
}