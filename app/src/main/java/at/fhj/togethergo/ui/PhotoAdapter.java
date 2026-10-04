package at.fhj.togethergo.ui;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import at.fhj.togethergo.data.entities.PhotoEntity;
import at.fhj.togethergo.databinding.ItemMomentBinding;
import at.fhj.togethergo.util.ImageUtils;

/**
 * RecyclerView adapter for the gallery of captured moments. Thumbnails are decoded on a
 * background thread to keep scrolling smooth.
 */
public class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.MomentViewHolder> {

    /** Callback for taps on a moment. */
    public interface OnMomentClickListener {
        /** Called when a moment is tapped. */
        void onMomentClick(PhotoEntity moment);
    }

    private List<PhotoEntity> moments = new ArrayList<>();
    private final OnMomentClickListener listener;

    // thumbnails are decoded off the main thread so scrolling stays smooth
    private final ExecutorService decodeExecutor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public PhotoAdapter(OnMomentClickListener listener) {
        this.listener = listener;
    }

    /** Replaces the displayed moments. */
    public void setMoments(List<PhotoEntity> moments) {
        this.moments = moments;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MomentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemMomentBinding binding = ItemMomentBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new MomentViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull MomentViewHolder holder, int position) {
        PhotoEntity moment = moments.get(position);

        holder.binding.ivMoment.setImageBitmap(null);
        // tag guards against a recycled holder showing the wrong photo once decoding finishes
        holder.binding.ivMoment.setTag(moment.filePath);
        if (decodeExecutor.isShutdown()) {
            return;
        }
        decodeExecutor.execute(() -> {
            // small, RGB_565 thumbnails keep memory low on weak devices
            Bitmap bitmap = ImageUtils.decodeSampled(moment.filePath, 240, 240, true);
            mainHandler.post(() -> {
                if (moment.filePath.equals(holder.binding.ivMoment.getTag())) {
                    holder.binding.ivMoment.setImageBitmap(bitmap);
                }
            });
        });

        holder.binding.getRoot().setOnClickListener(v -> {
            if (listener != null) {
                listener.onMomentClick(moment);
            }
        });
    }

    @Override
    public int getItemCount() {
        return moments.size();
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        // release the decode threads when the gallery goes away
        decodeExecutor.shutdownNow();
    }

    static class MomentViewHolder extends RecyclerView.ViewHolder {
        final ItemMomentBinding binding;

        MomentViewHolder(@NonNull ItemMomentBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
