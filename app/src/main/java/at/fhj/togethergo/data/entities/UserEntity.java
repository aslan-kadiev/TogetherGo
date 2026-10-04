package at.fhj.togethergo.data.entities;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Local copy of the user profile in the Room table {@code users}, so the UI can show data
 * quickly, even offline.
 */
@Entity(tableName = "users")
public class UserEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String name;
    public int level;
    public int currentPoints;
    public int totalQuests;

    public UserEntity() {
    }

    @Ignore
    public UserEntity(String name, int level, int currentPoints) {
        this.name = name;
        this.level = level;
        this.currentPoints = currentPoints;
        this.totalQuests = 0;
    }

    @Ignore
    public UserEntity(String name, int level, int currentPoints, int totalQuests) {
        this.name = name;
        this.level = level;
        this.currentPoints = currentPoints;
        this.totalQuests = totalQuests;
    }
}
