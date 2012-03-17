package ch.bergturbenthal.image.client.album;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import ch.bergturbenthal.image.client.resolver.AlbumService;
import ch.bergturbenthal.image.data.model.AlbumImageEntry;

public class AlbumThumbnailAdapter extends BaseAdapter {

  private final Context context;
  private final List<AlbumImageEntry> images = new ArrayList<AlbumImageEntry>();
  private final ConcurrentMap<String, File> cachedImages = new ConcurrentHashMap<String, File>();
  private AlbumService service = null;
  private final String albumId;

  public AlbumThumbnailAdapter(final Context context, final String albumId) {
    this.context = context;
    this.albumId = albumId;
  }

  @Override
  public int getCount() {
    if (service == null)
      return 0;
    return images.size();
  }

  @Override
  public AlbumImageEntry getItem(final int arg0) {
    return images.get(arg0);
  }

  @Override
  public long getItemId(final int arg0) {
    return 0;
  }

  @Override
  public View getView(final int position, final View convertView, final ViewGroup parent) {
    ImageView imageView;
    if (convertView == null) {
      imageView = new ImageView(context);
      imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
    } else {
      imageView = (ImageView) convertView;
    }
    final AlbumImageEntry imageEntry = images.get(position);
    final File foundDrawable = cachedImages.get(imageEntry.getId());
    if (foundDrawable != null) {
      imageView.setImageDrawable(new BitmapDrawable(context.getResources(), foundDrawable.getAbsolutePath()));
    }
    // else
    // imageView.setImageDrawable(context.getResources().getDrawable(R.drawable.logo));
    return imageView;
  }

  public void setImages(final Collection<AlbumImageEntry> entries, final Activity activity) {
    images.clear();
    final ExecutorService threadPool = Executors.newFixedThreadPool(3);
    for (final AlbumImageEntry albumImageEntry : entries) {
      images.add(albumImageEntry);
      threadPool.execute(new Runnable() {

        @Override
        public void run() {
          final File readImage = service.readImage(albumId, albumImageEntry.getId(), 128, 128);
          cachedImages.put(albumImageEntry.getId(), readImage);
          activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
              notifyDataSetChanged();
            }
          });
        }
      });
    }
    threadPool.shutdown();
    notifyDataSetChanged();
  }

  public void setService(final AlbumService service) {
    this.service = service;
    notifyDataSetChanged();
  }
}
