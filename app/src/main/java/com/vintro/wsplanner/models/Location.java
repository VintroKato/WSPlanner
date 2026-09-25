package com.vintro.wsplanner.models;

import android.net.Uri;
import com.vintro.wsplanner.enums.LocationType;
import java.io.Serializable;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// represents lesson location (room, online, or external)
public class Location implements Serializable {
    private final String rawValue;
    private final LocationType type;
    private final String displayText;
    private final String roomNumber;
    private final String mapQueryUrl;

    public Location(String rawInput) {
        this.rawValue = rawInput != null ? rawInput.trim() : "";
        String lower = this.rawValue.toLowerCase();

        if (lower.contains("online") || lower.contains("on-line") || lower.contains("puw") 
                || lower.contains("teams") || lower.contains("zoom") || lower.contains("zdaln")) {
            this.type = LocationType.ONLINE;
            this.displayText = "Online, PUW";
            this.roomNumber = null;
            this.mapQueryUrl = null;
        } else if (isOffsiteLocation(lower)) {
            this.type = LocationType.W_TERENIE;
            this.displayText = this.rawValue;
            this.roomNumber = null;
            this.mapQueryUrl = buildMapQueryUrl(this.rawValue, lower);
        } else if (lower.contains("sala") || lower.contains("s.") || lower.contains("uczelni") || lower.contains("wspa") || hasStandaloneRoomNumber(lower)) {
            this.type = LocationType.UCZELNIA;
            this.roomNumber = extractRoomNumber(this.rawValue);
            this.displayText = this.roomNumber != null ? "Sala " + this.roomNumber : (!this.rawValue.isEmpty() ? this.rawValue : "WSPA");
            this.mapQueryUrl = null;
        } else if (this.rawValue.isEmpty() || this.rawValue.equalsIgnoreCase("Unknown Room")) {
            this.type = LocationType.UCZELNIA;
            this.displayText = "WSPA";
            this.roomNumber = null;
            this.mapQueryUrl = null;
        } else {
            this.type = LocationType.W_TERENIE;
            this.displayText = this.rawValue;
            this.roomNumber = null;
            this.mapQueryUrl = buildMapQueryUrl(this.rawValue, lower);
        }
    }

    private static boolean isOffsiteLocation(String lower) {
        return lower.contains("ul.")
                || lower.contains("al.")
                || lower.contains("aleja")
                || lower.contains("plac")
                || lower.contains("lublin")
                || lower.matches(".*\\b\\d{2}-\\d{3}\\b.*")
                || lower.contains("fit")
                || lower.contains("siłownia")
                || lower.contains("basen")
                || lower.contains("stadion")
                || lower.contains("hala sportowa")
                || lower.contains("w terenie")
                || lower.contains("teren");
    }

    private static boolean hasStandaloneRoomNumber(String lower) {
        return lower.matches(".*(?<![\\d-])\\b\\d{3}[a-zA-Z]?\\b(?![\\d-]).*");
    }

    private static String buildMapQueryUrl(String raw, String lower) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String query = raw.replace("\n", ", ").trim();
        if (!lower.contains("lublin") && !lower.contains("polska")) {
            query += ", Lublin";
        }
        try {
            return "geo:0,0?q=" + java.net.URLEncoder.encode(query, "UTF-8");
        } catch (Exception e) {
            return "geo:0,0?q=" + query.replace(" ", "+");
        }
    }

    private String extractRoomNumber(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        String lower = text.toLowerCase();
        if (isOffsiteLocation(lower)) return null;

        Matcher explicitMatcher = Pattern.compile("(?i)(?:sal(?:a|i|ach)|s\\.?)\\s*([a-zA-Z0-9]+)").matcher(text);
        if (explicitMatcher.find()) {
            return explicitMatcher.group(1).toUpperCase();
        }

        Matcher numberMatcher = Pattern.compile("(?<![\\d-])\\b(\\d{3}[a-zA-Z]?)\\b(?![\\d-])").matcher(text);
        if (numberMatcher.find()) {
            return numberMatcher.group(1).toUpperCase();
        }
        return null;
    }

    public LocationType getType() {
        return type;
    }

    public String getDisplayText() {
        return displayText;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public String getRawValue() {
        return rawValue;
    }

    public String getMapQueryUrl() {
        return mapQueryUrl;
    }

    public boolean hasMapLink() {
        return mapQueryUrl != null;
    }

    public boolean isOnline() {
        return type == LocationType.ONLINE;
    }

    public boolean isOffsite() {
        return type == LocationType.W_TERENIE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Location)) return false;
        Location location = (Location) o;
        return type == location.type &&
                Objects.equals(displayText, location.displayText) &&
                Objects.equals(roomNumber, location.roomNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, displayText, roomNumber);
    }

    @Override
    public String toString() {
        return displayText;
    }
}
