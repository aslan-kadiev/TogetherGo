package at.fhj.togethergo.data.models;

import com.google.firebase.firestore.DocumentId;

/**
 * A quest as stored in Firestore. If {@link #type} is {@code "LOCATION"}, it is a
 * go-to-a-place quest shown as a marker on the map.
 */
public class Quest {
    @DocumentId
    public String id;
    public String title;
    public String description;
    public int points;
    public boolean isCompleted;
    public long completedAt;

    public String type;
    public double latitude;
    public double longitude;
    /** Radius in meters within which the location quest counts as reached. */
    public double radiusMeters;

    public Quest() {
        // Required for Firestore
    }

    public Quest(String title, String description, int points, boolean isCompleted) {
        this.title = title;
        this.description = description;
        this.points = points;
        this.isCompleted = isCompleted;
    }

    public boolean isLocationQuest() {
        return "LOCATION".equals(type);
    }

    /** Returns the quest radius in meters, defaulting to 75 m if none is set. */
    public double effectiveRadius() {
        return radiusMeters > 0 ? radiusMeters : 75.0;
    }
}
