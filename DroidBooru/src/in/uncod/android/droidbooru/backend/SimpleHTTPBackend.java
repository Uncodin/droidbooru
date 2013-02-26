package in.uncod.android.droidbooru.backend;

import in.uncod.android.droidbooru.BooruFile;
import in.uncod.android.droidbooru.net.FilesDownloadedCallback;
import in.uncod.android.droidbooru.net.FilesUploadedCallback;
import in.uncod.android.net.IConnectivityStatus;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;

import android.os.AsyncTask;
import android.os.Handler;

public class SimpleHTTPBackend extends Backend {
    private class QueryFilesTask extends AsyncTask<Object, Object, Object> {
        private final int number;
        private final int offset;
        private final FilesDownloadedCallback callback;

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
            this.number = number;
            this.offset = offset;
            this.callback = callback;
        }

        @Override
        protected Object doInBackground(Object... params) {
            HttpClient client = new DefaultHttpClient();
            HttpPost post = new HttpPost(mServerApiUrl);

            try {
                // Construct the request in JSON
                StringEntity requestEntity = new StringEntity("{ \"operation\" : \"query\", "
                        + "\"data\" : { \"image\" : { \"model\" : \"Image\", " + "\"limit\" : " + number
                        + ", \"offset\" : " + offset + ", \"order\" : \"uploadedDate DESC\" } } }");
                requestEntity.setContentType("application/json");
                post.setEntity(requestEntity);

                // Send request
                HttpResponse response = client.execute(post);

                if (response.getStatusLine().getStatusCode() != 200) {
                    throw new RuntimeException("Query failed; HTTP status "
                            + response.getStatusLine().getStatusCode());
                }

                // Read response data
                BufferedReader br = new BufferedReader(new InputStreamReader(
                        (response.getEntity().getContent())));

                StringBuilder output = new StringBuilder();
                String temp;
                while ((temp = br.readLine()) != null) {
                    output.append(temp);
                }

                // Parse response into a JSON array
                JSONArray files = new JSONObject(output.toString()).getJSONObject("data")
                        .getJSONObject("image").getJSONArray("slice");

                // Create a BooruFile for each file returned from the server
                BooruFile[] bFiles = new BooruFile[files.length()];
                for (int i = 0; i < files.length(); i++) {
                    JSONObject file = files.getJSONObject(i);

                    bFiles[i] = BooruFile.create(mDataDirectory.getAbsolutePath() + File.separator,
                            file.getString("mime"), file.getString("filehash"), file.getString("pid"),
                            mServerThumbRequestUrl.toURL(), mServerFileRequestUrl.toURL());
                }

                // Activate callback with results
                callback.onFilesDownloaded(offset, bFiles);
            }
            catch (Exception e) {
                e.printStackTrace();
            }

            return null;
        }
    }

    public SimpleHTTPBackend(File dataDirectory, File cacheDirectory, String serverAddress,
            IConnectivityStatus connectionChecker) {
        super(dataDirectory, cacheDirectory, serverAddress, connectionChecker);
    }

    @Override
    public boolean connect(BackendConnectedCallback callback) {
        if (!mConnectionChecker.canConnectToNetwork()) {
            return false;
        }

        // This backend doesn't auth
        callback.onBackendConnected(false);

        return true;
    }

    @Override
    public boolean uploadFiles(File[] files, String email, String tags, Handler uiHandler,
            FilesUploadedCallback callback) {
        if (!mConnectionChecker.canConnectToNetwork())
            return false;

        doFileUpload(files, email, tags, uiHandler, callback);

        return true;
    }

    @Override
    public boolean downloadFiles(int number, int offset, Handler uiHandler, FilesDownloadedCallback callback) {
        if (!mConnectionChecker.canConnectToNetwork())
            return false;

        queryExternalAndDownload(number, offset, uiHandler, callback);

        return true;
    }

    @Override
    public boolean queryExternalFiles(final int number, final int offset,
            final FilesDownloadedCallback callback) {
        if (!mConnectionChecker.canConnectToNetwork())
            return false;

        new QueryFilesTask(number, offset, callback).execute();

        return true;
    }
}
