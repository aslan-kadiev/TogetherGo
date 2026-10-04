package at.fhj.togethergo.data.entities;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * A captured moment. The image file lives in private app storage; this record (Room table
 * {@code moments}) holds its metadata.
 */
@Entity(tableName = "moments")
public class PhotoEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String filePath;   // absolute path to the image inside the app's private storage
    public String caption;    // optional note the user added
    public String promptId;   // id of the photo prompt this fulfils, or null for a free moment
    public String promptText; // resolved prompt title at capture time, for display in the gallery
    public String dateKey;    // yyyy-MM-dd, used to reward each daily prompt only once
    public long timestamp;

    public PhotoEntity() {
    }

    @Ignore
    public PhotoEntity(String filePath, String caption, String promptId,
                       String promptText, String dateKey, long timestamp) {
        this.filePath = filePath;
        this.caption = caption;
        this.promptId = promptId;
        this.promptText = promptText;
        this.dateKey = dateKey;
        this.timestamp = timestamp;
    }
}
