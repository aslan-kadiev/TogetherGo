package at.fhj.togethergo.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import at.fhj.togethergo.data.entities.UserEntity;

/** Room DAO for the locally stored user. */
@Dao
public interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertUser(UserEntity user);

    @Update
    void updateUser(UserEntity user);

    /** Observes the (single) stored user. */
    @Query("SELECT * FROM users LIMIT 1")
    LiveData<UserEntity> getUser();

    /** Returns the stored user synchronously. Call off the main thread only. */
    @Query("SELECT * FROM users LIMIT 1")
    UserEntity getUserSync();
}
