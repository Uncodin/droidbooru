package in.uncod.android.droidbooru;

import in.uncod.android.util.threading.TaskWithProgressAndListener;

import java.io.File;
import java.net.URI;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;

import android.app.ProgressDialog;
import android.util.Log;

/**
 * A task for uploading files to a nodebooru service.
 * 
 * Use the execute() method to start the upload in a background thread.
 */
public class BooruUploadTask extends TaskWithProgressAndListener<File, Void, Boolean> {
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
     * @param dialog
     *            If not null, the progress dialog will be automatically displayed and show the current progress towards
     *            upload completion
     */
    public BooruUploadTask(URI apiUrl, String email, String tags, OnTaskResultListener<Boolean> listener,
            ProgressDialog dialog) {
        super(listener, dialog);

        mApiUrl = apiUrl;
        mEmail = email;
        mTags = tags;
    }

    @Override
    protected Boolean doInBackground(File... params) {
        boolean error = false;

        HttpClient client = new DefaultHttpClient();
        HttpPost post = new HttpPost(mApiUrl);

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

            post.setEntity(entity);

            // Send the request
            Log.d(TAG, "Sending upload request...");
            client.execute(post);
        }
        catch (Exception e) {
            mException = e;
            error = true;
        }

        if (mException != null) {
            mException.printStackTrace();
        }

        return error;
    }
}
