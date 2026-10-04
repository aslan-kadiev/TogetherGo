package at.fhj.togethergo.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

import at.fhj.togethergo.data.entities.MoodEntryEntity;

/** Room DAO for mood entries. */
@Dao
public interface MoodDao {
    @Insert
    void insertMood(MoodEntryEntity mood);

    /** Returns all entries, newest first. */
    @Query("SELECT * FROM mood_entries ORDER BY timestamp DESC")
    LiveData<List<MoodEntryEntity>> getAllMoods();

    /** Returns the entries since the given epoch-millis timestamp, newest first. */
    @Query("SELECT * FROM mood_entries WHERE timestamp >= :since ORDER BY timestamp DESC")
    LiveData<List<MoodEntryEntity>> getMoodHistorySince(long since);
}
