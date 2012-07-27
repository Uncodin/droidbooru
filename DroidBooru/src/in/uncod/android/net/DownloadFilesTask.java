package in.uncod.android.net;

import in.uncod.android.util.threading.TaskWithProgressAndListener;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

import android.app.ProgressDialog;
import android.util.Log;

public class DownloadFilesTask extends TaskWithProgressAndListener<URL, Integer, List<File>> {
    private static final String TAG = "DownloadFilesTask";

    private File mDestinationPath;
    private boolean mOverwriteExisting;

    public DownloadFilesTask(File destinationPath, boolean overwriteExisting,
            OnResultListener<List<File>> listener, ProgressDialog dialog) {
        super(listener, dialog);

        if (destinationPath == null || !destinationPath.exists() || !destinationPath.isDirectory())
            throw new IllegalArgumentException("destinationPath must be an existing directory");

        mDestinationPath = destinationPath;
        mOverwriteExisting = overwriteExisting;
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        super.onProgressUpdate(values);

        if (mDialog != null) {
            int currentFileIndex = values[0];
            int numberOfFiles = values[1];
            int currentDownloadPercent = values[2];

            mDialog.setMax(100 * numberOfFiles);
            mDialog.setProgress((currentFileIndex * 100) + currentDownloadPercent);
        }
    }

    @Override
    protected List<File> doInBackground(URL... downloadUrls) {
        List<File> results = new ArrayList<File>(downloadUrls.length);

        int currentFileIndex = 0;

        // Initial progress state
        publishProgress(currentFileIndex, downloadUrls.length, 0);

        for (URL url : downloadUrls) {
            if (url == null)
                continue;

            try {
                // Determine destination file
                String[] urlSplits = url.toString().split("/");
                String filename = urlSplits[urlSplits.length - 1];
                File destinationFile = new File(mDestinationPath, filename);

                if (!mOverwriteExisting && destinationFile.exists()) {
                    // Consider file already downloaded
                    publishProgress(currentFileIndex, downloadUrls.length, 100);
                }
                else {
                    OutputStream output = new FileOutputStream(destinationFile);

                    Log.d(TAG, "Downloading file " + url + " to " + destinationFile.getAbsolutePath());

                    URLConnection connection = url.openConnection();
                    connection.connect();

                    // Get file size
                    int fileLength = connection.getContentLength();

                    // Download the file
                    InputStream input = new BufferedInputStream(url.openStream());

                    byte data[] = new byte[1024];
                    float total = 0;
                    int count;
                    while ((count = input.read(data)) != -1) {
                        total += count;

                        output.write(data, 0, count);

                        // Update progress
                        int currentDownloadPercent = (int) (100 * (total / fileLength));
                        publishProgress(currentFileIndex, downloadUrls.length, currentDownloadPercent);
                    }

                    output.flush();
                    output.close();
                    input.close();
                }

                results.add(destinationFile);
                currentFileIndex++;
            }
            catch (Exception e) {
                // TODO Remember data about failures and report them to listener
                e.printStackTrace();
            }
        }

        return results;
    }
}