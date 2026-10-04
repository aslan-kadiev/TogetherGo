package at.fhj.togethergo.data.entities;

import com.google.firebase.firestore.IgnoreExtraProperties;

/**
 * A meeting-point marker on the shared map.
 * <p>
 * Markers are stored in Firestore and visible to all users. Besides position and name, a marker
 * stores its author, an optional meeting time and an expiry time after which it is removed.
 */
@IgnoreExtraProperties
public class MapMarker {
    private String name;
    private double latitude;
    private double longitude;

    // author's display name, shown to every user
    private String createdByName;
    private String createdByUid;

    // optional meeting time the poster entered (epoch millis, 0 = none)
    private long meetTimeMillis;
    // when the marker should disappear from the map (epoch millis, <=0 = never)
    private long expiresAtMillis;
    private long createdAtMillis;

    public MapMarker() {
        // Required for Firestore
    }

    public MapMarker(String name, double latitude, double longitude,
                     String createdByName, String createdByUid,
                     long meetTimeMillis, long expiresAtMillis, long createdAtMillis) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.createdByName = createdByName;
        this.createdByUid = createdByUid;
        this.meetTimeMillis = meetTimeMillis;
        this.expiresAtMillis = expiresAtMillis;
        this.createdAtMillis = createdAtMillis;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public String getCreatedByName() {
        return createdByName;
    }

    public void setCreatedByName(String createdByName) {
        this.createdByName = createdByName;
    }

    public String getCreatedByUid() {
        return createdByUid;
    }

    public void setCreatedByUid(String createdByUid) {
        this.createdByUid = createdByUid;
    }

    public long getMeetTimeMillis() {
        return meetTimeMillis;
    }

    public void setMeetTimeMillis(long meetTimeMillis) {
        this.meetTimeMillis = meetTimeMillis;
    }

    public long getExpiresAtMillis() {
        return expiresAtMillis;
    }

    public void setExpiresAtMillis(long expiresAtMillis) {
        this.expiresAtMillis = expiresAtMillis;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public void setCreatedAtMillis(long createdAtMillis) {
        this.createdAtMillis = createdAtMillis;
    }

    /**
     * Returns whether the marker has expired at the given time. Uses the explicit expiry time
     * if set; legacy markers without one expire 24 hours after creation.
     */
    public boolean isExpired(long now) {
        if (expiresAtMillis > 0) {
            return expiresAtMillis < now;
        }
        // legacy markers had no explicit lifetime: fall back to 24h from creation,
        // and clean up truly ancient ones that carry no timestamps at all
        if (createdAtMillis > 0) {
            return createdAtMillis + 24L * 60 * 60 * 1000 < now;
        }
        return true;
    }
}
