package at.fhj.togethergo.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

import at.fhj.togethergo.data.entities.PhotoEntity;

/** Room DAO for locally stored photo moments. */
@Dao
public interface PhotoDao {
    @Insert
    long insert(PhotoEntity photo);

    @Delete
    void delete(PhotoEntity photo);

    @Query("SELECT * FROM moments ORDER BY timestamp DESC")
    LiveData<List<PhotoEntity>> getAll();
}
