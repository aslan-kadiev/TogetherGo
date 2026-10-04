package at.fhj.togethergo.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import at.fhj.togethergo.data.dao.MoodDao;
import at.fhj.togethergo.data.dao.PhotoDao;
import at.fhj.togethergo.data.dao.QuestDao;
import at.fhj.togethergo.data.dao.UserDao;
import at.fhj.togethergo.data.entities.MoodEntryEntity;
import at.fhj.togethergo.data.entities.PhotoEntity;
import at.fhj.togethergo.data.entities.QuestEntity;
import at.fhj.togethergo.data.entities.UserEntity;

/** Local Room database (singleton) with tables for users, quests, mood entries and moments. */
@Database(entities = {UserEntity.class, QuestEntity.class, MoodEntryEntity.class, PhotoEntity.class}, version = 4, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract UserDao userDao();

    public abstract QuestDao questDao();

    public abstract MoodDao moodDao();

    public abstract PhotoDao photoDao();

    /** Returns the shared database instance, creating it on first use (double-checked locking). */
    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "togethergo_database")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
