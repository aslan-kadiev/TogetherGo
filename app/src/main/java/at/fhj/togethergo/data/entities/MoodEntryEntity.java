package at.fhj.togethergo.data.entities;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * A single mood journal entry, stored both in Room (table {@code mood_entries}) and in
 * Firestore. {@link #firestoreId} is only used for editing and deleting.
 */
@Entity(tableName = "mood_entries")
public class MoodEntryEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public long timestamp;
    /** Mood from 1 (very bad) to 5 (very good). */
    public int moodLevel;
    public String note;

    /** Firestore document id, used for editing and deleting; not stored in Room. */
    @Ignore
    public String firestoreId;

    public MoodEntryEntity() {
    }

    @Ignore
    public MoodEntryEntity(int moodLevel, String note, long timestamp) {
        this.moodLevel = moodLevel;
        this.note = note;
        this.timestamp = timestamp;
    }
}
