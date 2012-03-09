/*******************************************************************************
 * Copyright 2012 Alexandros Schillings
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package co.uk.alt236.linkedinjtestapp.ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.SoftReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Stack;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.widget.ImageView;

public class ImageLoader {
	// in-memory cache implementation
	private static HashMap<String, SoftReference<Bitmap>> cache = new HashMap<String, SoftReference<Bitmap>>();
	
	private final String cache_dir_base;
	
	private final String cache_dir_ephemeral;
	private final String cache_dir_persistent;
	private File cacheDir;

	private final boolean cachePersintent;

	// Date threshold
	private final int maxCacheAge;
	
	private int maxPixels;
	private PhotosLoader photoLoaderThread = new PhotosLoader();
	private PhotosQueue photosQueue = new PhotosQueue();
	private int stub_id = android.R.drawable.gallery_thumb;
	private String TAG = this.getClass().getName();


	public ImageLoader(Context context, int placeholder, boolean persistentCache, int maxCacheAge, String cacheBaseDir) {
		this.cachePersintent = persistentCache;
		this.maxCacheAge = maxCacheAge;
		this.cache_dir_base = cacheBaseDir;
		this.cache_dir_persistent = cache_dir_base + "/persistent_cache";
		this.cache_dir_ephemeral = cache_dir_base + "/ephemeral_cache";
		init(context, placeholder, persistentCache);
	}

	public void clearCache() {
		cache.clear();

		File[] files = cacheDir.listFiles();
		
		for (File f : files){
			f.delete();
		}
	}

	public void clearMemory(ArrayList<String> keys, boolean recycle) {
		for (String key : keys) {
			clearMemory(key, recycle);
		}
	}

	public void clearMemory(boolean recycle) {

		Iterator<Entry<String, SoftReference<Bitmap>>> it = cache.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, SoftReference<Bitmap>> pairs = (Map.Entry<String, SoftReference<Bitmap>>) it.next();
			SoftReference<Bitmap> sr = (SoftReference<Bitmap>) pairs.getValue();
			if (sr.get() != null) {
				if (recycle) {
					sr.get().recycle();
				}
				sr.clear();
				sr = null;
			}
		}
		cache.clear();
		System.gc();

	}

	public void clearMemory(String key, boolean recycle) {
		if (cache.containsKey(key) && cache.get(key).get() != null) {
			SoftReference<Bitmap> sr = cache.get(key);
			cache.remove(sr);
			if (recycle) {
				sr.get().recycle();
			}
			sr.clear();
			sr = null;
		}
	}

	public void copyStream(InputStream is, OutputStream os) {
		final int buffer_size = 1024;
		try {
			byte[] bytes = new byte[buffer_size];
			for (;;) {
				int count = is.read(bytes, 0, buffer_size);
				if (count == -1)
					break;
				os.write(bytes, 0, count);
			}
		} catch (Exception ex) {
		}
	}

	private Bitmap decodeFile(File f, boolean withOptions) {
		int pixelBounds = maxPixels;
		Bitmap b = null;
		try {
			// Decode image size
			BitmapFactory.Options o = new BitmapFactory.Options();
			o.inJustDecodeBounds = true;
			BitmapFactory.decodeStream(new FileInputStream(f), null, o);
			if (o.outWidth <= 150) {
				pixelBounds = 75;
			}
			int scale = 1;
			if (o.outHeight > pixelBounds || o.outWidth > pixelBounds) {
				scale = (int) Math.pow(
						2,
						(int) Math.round(Math.log(pixelBounds
								/ (double) Math.max(o.outHeight, o.outWidth))
								/ Math.log(0.5)));
			}

			// Decode with inSampleSize
			BitmapFactory.Options o2 = new BitmapFactory.Options();
			o2.inSampleSize = scale;
			b = BitmapFactory.decodeStream(new FileInputStream(f), null,
					(withOptions) ? o2 : null);
		} catch (FileNotFoundException e) {
		}
		return b;
	}

	private void deleteFilesOlderThan(int maxAgeInDays, File directory) {
		Log.i(TAG, "^ Imageloader - Deleting files in " + directory.getPath() + " older than " + maxCacheAge + " days.");

		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DAY_OF_MONTH, maxAgeInDays * -1);
		long purgeTime = cal.getTimeInMillis();
		long fileCount = 0;

		if (directory.exists()) {

			File[] listFiles = directory.listFiles();
			for (File listFile : listFiles) {
				if (listFile.lastModified() < purgeTime) {
					if (!listFile.delete()) {
						Log.e(TAG, "^ Imageloader - Unable to delete file: "+ listFile);
					} else {
						fileCount += 1;
					}
				}
			}
		} else {
			Log.w(TAG,
					"^ Imageloader - Files were not deleted. '" + directory.getPath() + "' does not exist!");
		}
		Log.i(TAG, "^ Imageloader - Files deleted    :" + fileCount);
	}

	public void displayImage(String url, ImageView imageView) {
		if (cache.containsKey(url) && cache.get(url).get() != null) {
			imageView.setImageBitmap(cache.get(url).get());
		} else {
			queuePhoto(url, imageView);
			if (imageView.getDrawable() == null) {
				imageView.setImageResource(stub_id);
			}
		}
	}

	protected void finalize() throws Throwable {
		try {
			if (!isCachePersintent()) {
				invalidateCache();
			}
		} finally {
			super.finalize();
		}
	}

	private Bitmap getBitmap(String url, boolean withOptions) {
		// identify images by hashcode
		String filename = String.valueOf(url.hashCode());
		File f = new File(cacheDir, filename);

		// from SD cache
		Bitmap b = decodeFile(f, withOptions);
		if (b != null)
			return b;

		// from web
		try {
			Bitmap bitmap = null;

			HttpURLConnection conn = (HttpURLConnection) (new URL(url)
					.openConnection());
			conn.setRequestProperty("User-Agent", "Android");
			conn.setDoInput(true);
			conn.connect();

			InputStream is = conn.getInputStream();
			OutputStream os = new FileOutputStream(f);
			copyStream(is, os);
			os.close();
			bitmap = decodeFile(f, withOptions);

			return bitmap;
		} catch (Exception ex) {
			ex.printStackTrace();
			return null;
		}
	}

	public Bitmap getImageBitmap(String url) {
		// this method is synchronous!
		if (cache.containsKey(url) && cache.get(url).get() != null
				&& !cache.get(url).get().isRecycled()) {
			return cache.get(url).get();
		} else {
			Bitmap bmp = getBitmap(url, false);
			cache.put(url, new SoftReference<Bitmap>(bmp));
			return bmp;
		}
	}

	private void init(Context context, int placeholder, boolean persistentCache) {
		Log.d(TAG, "^ New Imageloader() - Persitent? " + persistentCache);

		// Make the background thread low priority. This way it will not affect
		// the UI performance
		photoLoaderThread.setPriority(Thread.NORM_PRIORITY - 1);

		// Find the dir to save cached images
		if (android.os.Environment.getExternalStorageState().equals(
				android.os.Environment.MEDIA_MOUNTED)) {
			String cache_dir = (persistentCache) ? cache_dir_persistent
					: cache_dir_ephemeral;
			cacheDir = new File(
					android.os.Environment.getExternalStorageDirectory(),
					cache_dir);
		} else {
			cacheDir = context.getCacheDir();
		}

		if (!cacheDir.exists())
			cacheDir.mkdirs();

		maxPixels = 128;

		if (placeholder >= 0) {
			stub_id = placeholder;
		}
	}

	public void invalidateCache() {
		deleteFilesOlderThan(maxCacheAge, cacheDir);
	}

	public boolean isCachePersintent() {
		return cachePersintent;
	}

	private void queuePhoto(String url, ImageView imageView) {
		// This ImageView may be used for other images before. So there may be
		// some old tasks in the queue. We need to discard them.
		photosQueue.Clean(imageView);
		PhotoToLoad p = new PhotoToLoad(url, imageView);
		synchronized (photosQueue.photosToLoad) {
			photosQueue.photosToLoad.push(p);
			photosQueue.photosToLoad.notifyAll();
		}

		// start thread if it's not started yet
		if (photoLoaderThread.getState() == Thread.State.NEW)
			photoLoaderThread.start();
	}

	public void stopThread() {
		photoLoaderThread.interrupt();
	}

	// Used to display bitmap in the UI thread
	class BitmapDisplayer implements Runnable {
		Bitmap bitmap;
		int cornerRadius;
		ImageView imageView;

		public BitmapDisplayer(Bitmap b, ImageView i) {
			bitmap = b;
			imageView = i;
		}

		public void run() {
			if (bitmap != null) {
				imageView.setImageBitmap(bitmap);
			} else {
				if (imageView.getDrawable() == null) { // ***
					imageView.setImageResource(android.R.drawable.ic_delete);
				}
			}
		}
	}

	class PhotosLoader extends Thread {
		@Override
		public void run() {
			try {
				while (true) {
					// thread waits until there are any images to load in the
					// queue
					if (photosQueue.photosToLoad.size() == 0)
						synchronized (photosQueue.photosToLoad) {
							photosQueue.photosToLoad.wait();
						}
					if (photosQueue.photosToLoad.size() != 0) {
						PhotoToLoad photoToLoad;
						synchronized (photosQueue.photosToLoad) {
							photoToLoad = photosQueue.photosToLoad.pop();
						}
						Bitmap bmp = getBitmap(photoToLoad.url, true);
						cache.put(photoToLoad.url, new SoftReference<Bitmap>(
								bmp));
						if (((String) photoToLoad.imageView.getTag())
								.equals(photoToLoad.url)) {
							BitmapDisplayer bd = new BitmapDisplayer(bmp,
									photoToLoad.imageView);
							Activity a = (Activity) photoToLoad.imageView
									.getContext();
							a.runOnUiThread(bd);
							a = null; // !
						}
					}
					if (Thread.interrupted()) {
						// break;
						return;
					}
					if (isInterrupted()) {
						return;
					}
				}
			} catch (InterruptedException e) {
				// allow thread to exit
				return;
			} catch (Exception e) {
				return;
			}
		}
	}

	// stores list of photos to download
	class PhotosQueue {
		private Stack<PhotoToLoad> photosToLoad = new Stack<PhotoToLoad>();

		// removes all instances of this ImageView
		public void Clean(ImageView image) {
			for (int j = 0; j < photosToLoad.size();) {
				if (photosToLoad.get(j).imageView == image)
					photosToLoad.remove(j);
				else
					++j;
			}
		}
	}

	// Task for the queue
	private class PhotoToLoad {
		public ImageView imageView;
		public String url;

		public PhotoToLoad(String u, ImageView i) {
			url = u;
			imageView = i;
		}
	}

}
