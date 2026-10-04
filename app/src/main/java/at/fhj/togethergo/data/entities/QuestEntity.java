package at.fhj.togethergo.data.entities;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/** A quest stored locally in the Room table {@code quests}. */
@Entity(tableName = "quests")
public class QuestEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String title;
    public String description;
    public int rewardPoints;
    public boolean isCompleted;

    public QuestEntity() {
    }

    @Ignore
    public QuestEntity(String title, String description, int rewardPoints) {
        this.title = title;
        this.description = description;
        this.rewardPoints = rewardPoints;
        this.isCompleted = false;
    }
}
