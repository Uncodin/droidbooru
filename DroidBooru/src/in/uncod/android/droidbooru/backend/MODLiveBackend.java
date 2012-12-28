package in.uncod.android.droidbooru.backend;

import in.uncod.android.droidbooru.BooruFile;
import in.uncod.android.droidbooru.net.FilesDownloadedCallback;
import in.uncod.android.droidbooru.net.FilesUploadedCallback;
import in.uncod.android.net.IConnectivityStatus;
import in.uncod.android.util.threading.TaskWithResultListener.OnTaskResultListener;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;

import com.reconinstruments.webapi.SDKWebService;
import com.reconinstruments.webapi.SDKWebService.WebResponseListener;
import com.reconinstruments.webapi.WebRequestMessage.WebMethod;
import com.reconinstruments.webapi.WebRequestMessage.WebRequestBundle;

public class MODLiveBackend extends Backend {
    private class QueryFilesTask extends AsyncTask<Object, Object, Object> {
        private final int mNumber;
        private final int mOffset;
        private final FilesDownloadedCallback mCallback;

        /**
         * Queries for nodebooru file metadata, in reverse order from the uploaded date
         * 
         * @param number
         *            The number of files to retrieve
         * @param offset
         *            The number of files to skip
         * @param callback
         *            This callback is activated when the metadata has been retrieved
         */
        private QueryFilesTask(int number, int offset, FilesDownloadedCallback callback) {
            this.mNumber = number;
            this.mOffset = offset;
            this.mCallback = callback;
        }

        @Override
        protected Object doInBackground(Object... params) {
            // Construct the request in JSON
            JSONObject request = null;
            try {
                request = new JSONObject("{ \"operation\" : \"query\", "
                        + "\"data\" : { \"image\" : { \"model\" : \"Image\", " + "\"limit\" : " + mNumber
                        + ", \"offset\" : " + mOffset + ", \"order\" : \"uploadedDate DESC\" } } }");
            }
            catch (JSONException e) {
                e.printStackTrace();

                return null;
            }

            List<NameValuePair> headers = new ArrayList<NameValuePair>();
            headers.add(new BasicNameValuePair("Content-type", "application/json"));

            WebRequestBundle wrb = new WebRequestBundle("", mServerApiUrl.toString(), WebMethod.POST,
                    "queryFiles", headers, request);

            WebResponseListener listener = new WebResponseListener() {
                @Override
                public void onComplete(byte[] response, String statusCode, String statusId, String requestId) {
                    // Not used
                }

                @Override
                public void onComplete(String responseBody, String strStatusCode, String statusId,
                        String requestId) {
                    int statusCode = Integer.parseInt(strStatusCode);

                    if (statusCode != HttpStatus.SC_OK) {
                        throw new RuntimeException("Query failed; HTTP status " + strStatusCode);
                    }

                    // Parse response into a JSON array
                    JSONArray files;
                    BooruFile[] bFiles = null;
                    try {
                        files = new JSONObject(responseBody).getJSONObject("data").getJSONObject("image")
                                .getJSONArray("slice");

                        // Create a BooruFile for each file returned from the server
                        bFiles = new BooruFile[files.length()];
                        for (int i = 0; i < files.length(); i++) {
                            JSONObject file = files.getJSONObject(i);

                            bFiles[i] = BooruFile.create(mDataDirectory.getAbsolutePath() + File.separator,
                                    file.getString("mime"), file.getString("filehash"),
                                    file.getString("pid"), mServerThumbRequestUrl.toURL(),
                                    mServerFileRequestUrl.toURL());
                        }
                    }
                    catch (Exception e) {
                        e.printStackTrace();

                        Log.e(TAG, "Error parsing received data: " + responseBody);
                    }

                    // Activate callback with results
                    mCallback.onFilesDownloaded(mOffset, bFiles);
                }
            };

            // Send request
            Log.d(TAG, "Sending request " + request);
            SDKWebService.httpRequest(mContext, false, 10 * 1000, wrb, listener);

            return null;
        }
    }

    /**
     * MOD Live-specific implementation of download task
     */
    private class DownloadFilesTask extends in.uncod.android.net.DownloadFilesTask {
        private class DownloadFileListener implements WebResponseListener {
            private final OutputStream mOutput;

            public boolean finished;
            public IOException exception;

            private DownloadFileListener(OutputStream output) {
                this.mOutput = output;
            }

            @Override
            public void onComplete(byte[] response, String statusCode, String statusId, String requestId) {
                try {
                    mOutput.write(response);

                    finished = true;
                }
                catch (IOException e) {
                    e.printStackTrace();

                    finished = true;
                    exception = e;
                }
            }

            @Override
            public void onComplete(String responseBody, String strStatusCode, String statusId,
                    String requestId) {
                // Not used
            }
        }

        public DownloadFilesTask(File destinationPath, boolean overwriteExisting,
                OnTaskResultListener<List<File>> listener, ProgressDialog dialog) {
            super(destinationPath, overwriteExisting, listener, dialog);
        }

        @Override
        protected void downloadFileFromUrl(URL url, final OutputStream output, int currentFileIndex,
                int totalFileCount) throws IOException {
            WebRequestBundle wrb = new WebRequestBundle("", url.toString(), WebMethod.GET, "getFile", null,
                    "");

            DownloadFileListener listener = new DownloadFileListener(output);

            SDKWebService.httpRequest(mContext, false, 10 * 1000, wrb, listener);

            while (!listener.finished)
                ;

            if (listener.exception != null) {
                throw listener.exception;
            }
        }
    }

    /**
     * MOD Live-specific implementation of upload task
     */
    private class BooruUploadTask extends in.uncod.android.droidbooru.net.BooruUploadTask {
        public BooruUploadTask(URI apiUrl, String email, String tags, OnTaskResultListener<Boolean> listener,
                ProgressDialog dialog) {
            super(apiUrl, email, tags, listener, dialog);
        }

        @Override
        protected void sendUploadRequest(URI url, MultipartEntity entity) throws IOException {
            WebRequestBundle wrb = new WebRequestBundle("", url.toString(), WebMethod.POST, "uploadFile",
                    null, EntityUtils.toString(entity));

            WebResponseListener listener = new WebResponseListener() {
                @Override
                public void onComplete(String arg0, String arg1, String arg2, String arg3) {
                    // Not used
                }

                @Override
                public void onComplete(byte[] arg0, String arg1, String arg2, String arg3) {
                    // Not used
                }
            };

            SDKWebService.httpRequest(mContext, false, 10 * 1000, wrb, listener);
        }
    }

    private Context mContext;

    public MODLiveBackend(Context context, File dataDirectory, File cacheDirectory, String serverAddress,
            IConnectivityStatus connectionChecker) {
        super(dataDirectory, cacheDirectory, serverAddress, connectionChecker);

        mContext = context;
    }

    @Override
    public boolean connect(BackendConnectedCallback callback) {
        // This backend doesn't auth
        callback.onBackendConnected(false);

        return true;
    }

    @Override
    public boolean uploadFiles(final File[] files, final String email, final String tags, Handler uiHandler,
            final FilesUploadedCallback callback, final ProgressDialog dialog) {
        uiHandler.post(new Runnable() {
            public void run() {
                new BooruUploadTask(mServerFilePostUrl, email, tags, new OnTaskResultListener<Boolean>() {
                    public void onTaskResult(Boolean error) {
                        if (callback != null) {
                            callback.onFilesUploaded(error);
                        }
                    }
                }, dialog).execute(files);
            }
        });

        return true;
    }

    @Override
    public boolean downloadFiles(int number, int offset, final Handler uiHandler,
            final ProgressDialog dialog, final FilesDownloadedCallback callback) {
        Log.d(TAG, "Downloading " + number + " files from offset " + offset);
        queryExternalFiles(number, offset, new FilesDownloadedCallback() {
            public void onFilesDownloaded(final int offset, final BooruFile[] files) {
                if (files.length > 0) {
                    uiHandler.post(new Runnable() {

                        public void run() {
                            // Download thumbnails
                            new DownloadFilesTask(mDataDirectory, false,
                                    new OnTaskResultListener<List<File>>() {
                                        public void onTaskResult(List<File> dFiles) {
                                            // Associate the files with each BooruFile
                                            int i = 0;
                                            for (File file : dFiles) {
                                                files[i].setThumbFile(file);
                                                i++;
                                            }

                                            if (callback != null)
                                                callback.onFilesDownloaded(offset, files);
                                        }
                                    }, dialog).execute(getThumbUrlsForBooruFiles(files));
                        }
                    });
                }
                else {
                    if (callback != null)
                        callback.onFilesDownloaded(offset, new BooruFile[0]);
                }
            }
        });

        return true;
    }

    @Override
    public boolean queryExternalFiles(final int number, final int offset,
            final FilesDownloadedCallback callback) {

        new QueryFilesTask(number, offset, callback).execute();

        return true;
    }
}
