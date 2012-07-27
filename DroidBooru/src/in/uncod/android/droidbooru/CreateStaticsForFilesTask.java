package in.uncod.android.droidbooru;

import in.uncod.android.util.threading.TaskWithProgressAndListener;
import in.uncod.nativ.Image;
import in.uncod.nativ.KeyPredicate;
import in.uncod.nativ.ORMDatastore;
import in.uncod.nativ.Static;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import android.app.ProgressDialog;
import android.util.Log;

class CreateStaticsForFilesTask extends TaskWithProgressAndListener<File, Integer, List<Static>> {
    private static final String TAG = "CreateStaticsForImagesTask";

    private ORMDatastore mDatastore;

    CreateStaticsForFilesTask(ORMDatastore datastore, OnResultListener<List<Static>> listener,
            ProgressDialog dialog) {
        super(listener, dialog);

        mDatastore = datastore;
    }

    protected void onPreExecute() {
        mDialog.setTitle(R.string.processing);
        mDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);

        super.onPreExecute();
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        super.onProgressUpdate(values);

        if (mDialog != null) {
            int lastFile = values[0];
            int totalFiles = values[1];

            mDialog.setMax(totalFiles);
            mDialog.setProgress(lastFile);
        }
    }

    @Override
    protected List<Static> doInBackground(File... filesToProcess) {
        List<Static> statics = new ArrayList<Static>();

        int totalFiles = filesToProcess.length;
        int currentFile = 0;

        for (File file : filesToProcess) {
            // Determine image by looking at file hash
            String hash = file.getName().split("\\.")[0].replace("_thumb", "");
            Image[] image = mDatastore.selectImage(KeyPredicate.defaultPredicate().where(
                    "filehash == " + hash));

            if (image.length > 0) {
                statics.add(getStaticForFile(file, hash));
            }

            currentFile++;
            publishProgress(currentFile, totalFiles);
        }

        return statics;
    }

    private Static getStaticForFile(File file, String hash) {
        Static[] statics = mDatastore.selectStatic(KeyPredicate.defaultPredicate().where(
                "SHA1Hash == " + hash));
        Static resultStatic;

        if (statics.length == 0) {
            // Create a Static for the image file
            resultStatic = mDatastore.createStaticFromFile(file);
            resultStatic.setSHA1Hash(hash);
            mDatastore.update(resultStatic);

            Log.d(TAG, "Added Static for " + file.getAbsolutePath());
        }
        else {
            // Found a Static for the file
            resultStatic = statics[0];
        }

        return resultStatic;
    }
}