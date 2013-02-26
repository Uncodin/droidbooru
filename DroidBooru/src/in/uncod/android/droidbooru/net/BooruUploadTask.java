package in.uncod.android.droidbooru.net;

import in.uncod.android.util.threading.TaskWithResultListener;

import java.io.File;
import java.io.IOException;
import java.net.URI;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;

import android.util.Log;

/**
 * A task for uploading files to a nodebooru service.
 * 
 * Use the execute() method to start the upload in a background thread.
 */
public class BooruUploadTask extends TaskWithResultListener<File, Void, Boolean> {
    private static final String TAG = "BooruUploadTask";

    private URI mApiUrl;
    private String mEmail;
    private String mTags;
    private Exception mException;

    /**
     * Creates a new upload task
     * 
     * @param apiUrl
     *            The URL of the nodebooru upload API
     * @param email
     *            The uploading user's email address
     * @param tags
     *            A comma-delimited list of tags, e.g. "animal,pop tart,cat"
     * @param listener
     *            If not null, the result listener will be activated once all uploads have completed
     */
    public BooruUploadTask(URI apiUrl, String email, String tags, OnTaskResultListener<Boolean> listener) {
        super(listener);

        mApiUrl = apiUrl;
        mEmail = email;
        mTags = tags;
    }

    @Override
    protected Boolean doInBackground(File... params) {
        // Build the POST request
        MultipartEntity entity = new MultipartEntity();

        int i = 0;
        for (File file : params) {
            if (file != null && file.exists() && file.length() > 0) {
                entity.addPart("file" + i, new FileBody(file));
            }

            i++;
        }

        try {
            entity.addPart("email", new StringBody(mEmail));
            entity.addPart("tags", new StringBody(mTags));

            Log.d(TAG, "Sending upload request...");

            // Send the request
            sendUploadRequest(mApiUrl, entity);
        }
        catch (Exception e) {
            mException = e;
        }

        if (mException != null) {
            mException.printStackTrace();
        }

        return mException != null;
    }

    /**
     * Sends an upload request via HTTP POST
     * 
     * @param url
     *            URL to post request
     * @param entity
     *            Request data
     * 
     * @throws IOException
     *             Thrown if there is an error sending the request
     */
    protected void sendUploadRequest(URI url, MultipartEntity entity) throws IOException {
        HttpClient client = new DefaultHttpClient();
        HttpPost post = new HttpPost(url);

        post.setEntity(entity);

        client.execute(post);
    }
}
