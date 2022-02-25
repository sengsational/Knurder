package com.sengsational.knurder;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by Owner on 3/13/2017.
 */

public class FileHelper {
    private static final String TAG = FileHelper.class.getSimpleName();

    private static final String JPEG_FILE_PREFIX = "IMG_";
    private static final String JPEG_FILE_SUFFIX = ".jpg";

    private static File THE_FILE = null;
    private File aFile = null;

    public static File createTempPhotoFile() throws IOException {
        Log.v(TAG, "createTempPhotoFile");
        THE_FILE = createTempFileInAlbumDirectory();
        return THE_FILE;
    }

    public static String getFileNameStringForTempPhotoFile(boolean forceNew) {
        try {
            if (THE_FILE == null || forceNew) createTempPhotoFile();
        } catch (IOException e) {
            Log.e(TAG, "Unable to get file name. " + e.getMessage());
            return "/storage/emulated/0/dcim/Knurder/" + JPEG_FILE_PREFIX + "ERROR" + Math.random() + "_." + JPEG_FILE_SUFFIX;
        }
        return THE_FILE.getAbsolutePath();
    }

    public String getFileNameForTesting(String glass) throws IOException {
        aFile = new File("/storage/emulated/0/dcim/Knurder/" + glass + ".PNG");
        return aFile.getAbsolutePath();
    }


    private static File createTempFileInAlbumDirectory() throws IOException {
        Log.v(TAG, "createTempFileInAlbumDirectory");
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = JPEG_FILE_PREFIX + timeStamp + "_";
        File albumF = getAlbumDir();
        File imageF = File.createTempFile(imageFileName, JPEG_FILE_SUFFIX, albumF);
        return imageF;
    }

    private static File getAlbumDir() {
        File storageDir = null;
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            storageDir = new File (Environment.getExternalStorageDirectory() + "/dcim/" + "Knurder" );

            if (storageDir != null) {
                if (! storageDir.mkdirs()) {
                    if (! storageDir.exists()){
                        Log.d(TAG, "failed to create directory");
                        return null;
                    }
                }
            }
        } else {
            Log.v(TAG, "External storage is not mounted READ/WRITE.");
        }
        return storageDir;
    }

    public static void deleteTheFile() {
        if (THE_FILE != null && THE_FILE.isFile() && THE_FILE.exists()) {
            Log.v(TAG, "The file  was " + THE_FILE.getAbsolutePath() + " and we're deleting it.");
            try {
                THE_FILE.delete();
            } catch (Throwable t) {
                Log.v(TAG, "Could not delete the file. " + t.getMessage());
            }
        } else {
            if (THE_FILE != null) {
                Log.v(TAG, "The file was " + THE_FILE + " exists: " + THE_FILE.exists());
            } else {
                Log.v(TAG, "The file variable was null.");
            }
        }
    }
}

