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
        } else if (lower.contains("sala") || lower.matches(".*\\b\\d{3}[a-zA-Z]?\\b.*") || lower.contains("uczelni") || lower.contains("wspa")) {
            this.type = LocationType.UCZELNIA;
            this.roomNumber = extractRoomNumber(this.rawValue);
            this.displayText = this.roomNumber != null ? "Sala " + this.roomNumber : (!this.rawValue.isEmpty() ? this.rawValue : "WSPA");
            this.mapQueryUrl = null;
        } else if (this.rawValue.isEmpty()) {
            this.type = LocationType.UCZELNIA;
            this.displayText = "WSPA";
            this.roomNumber = null;
            this.mapQueryUrl = null;
        } else {
            this.type = LocationType.W_TERENIE;
            this.displayText = this.rawValue;
            this.roomNumber = null;
            if (lower.contains("ul.") || lower.contains("lublin") || lower.contains("fit")) {
                String encoded;
                try {
                    encoded = java.net.URLEncoder.encode(this.rawValue, "UTF-8");
                } catch (Exception e) {
                    encoded = this.rawValue.replace(" ", "+");
                }
                this.mapQueryUrl = "geo:0,0?q=" + encoded;
            } else {
                this.mapQueryUrl = null;
            }
        }
    }

    private String extractRoomNumber(String text) {
        Matcher matcher = Pattern.compile("(?i)(?:sala\\s*)?(\\b\\d{3}[a-zA-Z]?\\b)").matcher(text);
        return matcher.find() ? matcher.group(1) : null;
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
