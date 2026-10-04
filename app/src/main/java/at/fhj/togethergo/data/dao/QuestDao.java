package at.fhj.togethergo.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import at.fhj.togethergo.data.entities.QuestEntity;

/** Room DAO for locally stored quests. */
@Dao
public interface QuestDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertQuest(QuestEntity quest);

    @Update
    void updateQuest(QuestEntity quest);

    /** Returns the first quest that is not completed yet. */
    @Query("SELECT * FROM quests WHERE isCompleted = 0 LIMIT 1")
    LiveData<QuestEntity> getActiveQuest();

    /** Returns all quests, newest first. */
    @Query("SELECT * FROM quests ORDER BY id DESC")
    LiveData<List<QuestEntity>> getAllQuests();

    /** Returns all completed quests, newest first. */
    @Query("SELECT * FROM quests WHERE isCompleted = 1 ORDER BY id DESC")
    LiveData<List<QuestEntity>> getCompletedQuests();

    /** Resets all quests to open; used when the day changes. */
    @Query("UPDATE quests SET isCompleted = 0")
    void resetAllQuests();
}
