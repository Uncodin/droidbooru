package in.uncod.android.droidbooru;

import in.uncod.android.net.DownloadFilesTask;
import in.uncod.android.util.threading.TaskWithResultListener.OnResultListener;
import in.uncod.nativ.AbstractNetworkCallbacks;
import in.uncod.nativ.HttpClientNetwork;
import in.uncod.nativ.INetworkHandler;
import in.uncod.nativ.Image;
import in.uncod.nativ.KeyPredicate;
import in.uncod.nativ.ORMDatastore;
import in.uncod.nativ.ORMDownloadParameters;
import in.uncod.nativ.Static;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.apache.http.HttpHost;

import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.MimeTypeMap;
import android.webkit.WebView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem.OnMenuItemClickListener;

public class MainActivity extends SherlockActivity {
    private final class AuthCallback extends AbstractNetworkCallbacks {
        @Override
        public void onError(INetworkHandler handler, int errorCode, String message) {
            runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(MainActivity.this, "Login failed", Toast.LENGTH_LONG).show();
                }
            });
        }

        @Override
        public void finished(final String extras) {
            downloadFiles(5, 0, new Runnable() {
                public void run() {
                    if (mBooruStatics.size() > 0) {
                        mConnectButton.setVisibility(View.GONE);
                        mControls.setVisibility(View.VISIBLE);
                        mImageView.setVisibility(View.VISIBLE);

                        displayImage(mImageIndex);
                    }
                    else {
                        mConnectButton.setEnabled(true);
                    }
                }
            });
        }
    }

    private static final String TAG = "MainActivity";

    /**
     * Request code for uploading a file
     */
    private static final int REQ_CODE_UPLOAD_FILE = 0;

    private List<Static> mBooruStatics = new ArrayList<Static>();
    private ORMDatastore mDatastore;
    private View mConnectButton;
    private WebView mImageView;
    private File mDataDirectory;
    private View mNextButton;
    private int mImageIndex = 0;
    private View mControls;
    private View mPreviousButton;
    private View mLaunchButton;
    private Intent mLaunchIntent;
    private View mUploadButton;
    private URI mServerAddress;
    private URI mServerNativApiUrl;
    private URI mServerFilePostUrl;
    private URI mServerFileRequestUrl;
    private URI mServerThumbRequestUrl;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Login settings
        menu.add(R.string.settings).setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_NEVER)
                .setOnMenuItemClickListener(new OnMenuItemClickListener() {
                    public boolean onMenuItemClick(com.actionbarsherlock.view.MenuItem item) {
                        startActivity(new Intent(MainActivity.this, LoginSettingsActivity.class));

                        return false;
                    }
                });

        return super.onCreateOptionsMenu(menu);
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Resources resources = getResources();

        // Get server address
        mServerAddress = URI.create(HttpHost.DEFAULT_SCHEME_NAME
                + "://"
                + getPreferences(MODE_PRIVATE).getString(resources.getString(R.string.pref_selected_server),
                        resources.getString(R.string.dv_pref_selected_server)));

        // Setup various endpoints
        mServerNativApiUrl = URI.create(mServerAddress + "/v2/api");
        mServerFilePostUrl = URI.create(mServerAddress + "/upload/curl");
        mServerFileRequestUrl = URI.create(mServerAddress + "/img/");
        mServerThumbRequestUrl = URI.create(mServerAddress + "/thumb/");

        // Set data directory
        mDataDirectory = getExternalFilesDir(null);
        Log.d(TAG, "Using data directory: " + mDataDirectory.getAbsolutePath());

        initializeUI();

        Intent intent = getIntent();
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            // Started via Share request
            Log.d(TAG, "Received single file upload request");

            // Prepare to refresh after upload
            initAndAuthNativ();

            // Uploading a single file
            uploadFiles(new File[] { getFileForUri((Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM)) });
        }
        else if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
            // Started via Share request
            Log.d(TAG, "Received multiple file upload request");

            // Prepare to refresh after upload
            initAndAuthNativ();

            // Uploading multiple files
            ArrayList<Uri> fileUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            File[] files = new File[fileUris.size()];

            int i = 0;
            for (Uri uri : fileUris) {
                files[i] = getFileForUri(uri);

                i++;
            }

            uploadFiles(files);
        }
    }

    private void initAndAuthNativ() {
        mDatastore = ORMDatastore.create(new File(mDataDirectory, "droidbooru.db").getAbsolutePath());
        mDatastore.setDownloadPathPrefix(mDataDirectory.getAbsolutePath() + File.separator);
        mDatastore.setNetworkHandler(new HttpClientNetwork(mServerNativApiUrl.toString()));
        mDatastore.authenticate("test", "test", new AuthCallback());
    }

    private void initializeUI() {
        mControls = findViewById(R.id.controls);

        mConnectButton = findViewById(R.id.button_connect);
        mConnectButton.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {
                mConnectButton.setEnabled(false);

                initAndAuthNativ();
            }
        });

        mPreviousButton = findViewById(R.id.button_previous);
        mPreviousButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                mImageIndex--;

                if (mImageIndex < 0) {
                    mImageIndex = 0;
                }
                else {
                    displayImage(mImageIndex);
                }
            }
        });

        mNextButton = findViewById(R.id.button_next);
        mNextButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                mNextButton.setEnabled(false);

                mImageIndex++;

                if (mImageIndex == mBooruStatics.size()) {
                    downloadFiles(5, mBooruStatics.size(), new Runnable() {
                        public void run() {
                            displayImage(mImageIndex);
                            mNextButton.setEnabled(true);
                        }
                    });
                }
                else {
                    displayImage(mImageIndex);
                    mNextButton.setEnabled(true);
                }
            }
        });

        mLaunchButton = findViewById(R.id.button_launch);
        mLaunchButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                try {
                    startActivity(mLaunchIntent);
                }
                catch (ActivityNotFoundException e) {
                    e.printStackTrace();

                    Toast.makeText(MainActivity.this, "Sorry, your device can't view the original file!",
                            Toast.LENGTH_LONG).show();
                }
            }
        });

        mImageView = (WebView) findViewById(R.id.image);

        mLaunchIntent = new Intent();
        mLaunchIntent.setAction(android.content.Intent.ACTION_VIEW);

        mUploadButton = findViewById(R.id.button_upload);
        mUploadButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                showFileChooser();
            }
        });
    }

    private void showFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        startActivityForResult(intent, REQ_CODE_UPLOAD_FILE);
    }

    private File getFileForUri(Uri uri) {
        File retFile = null;

        if (uri.getScheme().equals(ContentResolver.SCHEME_CONTENT)) {
            try {
                // Copy the file to our cache
                retFile = File.createTempFile("upload_", ".tmp", getExternalCacheDir());

                InputStream inStream = getContentResolver().openInputStream(uri);
                FileOutputStream outStream = new FileOutputStream(retFile);

                byte[] buffer = new byte[1024];
                while (inStream.read(buffer) != -1) {
                    outStream.write(buffer);
                }

                inStream.close();
                outStream.close();
            }
            catch (IOException e) {
                e.printStackTrace();

                // Delete the temp file and return null
                retFile.delete();
                retFile = null;
            }
        }
        else if (uri.getScheme().equals(ContentResolver.SCHEME_FILE)) {
            retFile = new File(uri.getPath());
        }

        return retFile;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
        case REQ_CODE_UPLOAD_FILE:
            if (resultCode == RESULT_OK) {
                // Upload chosen file
                Uri uri = data.getData();
                Log.d(TAG, "Upload request for " + uri);

                File uploadFile = getFileForUri(uri);

                if (uploadFile != null) {
                    uploadFiles(new File[] { uploadFile });
                }
            }
            break;
        default:
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void uploadFiles(final File[] files) {
        String email = "droidbooru@ironclad.mobi";
        String tags = "droidbooru,"
                + new SimpleDateFormat("MM-dd-yy").format(Calendar.getInstance().getTime());

        new BooruUploadTask(mServerFilePostUrl, email, tags, new OnResultListener<Void>() {
            public void onTaskResult(Void result) {
                // Download and display the image(s) that were just uploaded
                downloadFiles(files.length, 0, new Runnable() {
                    public void run() {
                        mImageIndex = 0;
                        displayImage(mImageIndex);
                    }
                });
            }
        }, null).execute(files);
    }

    private List<URL> getThumbUrlsForFiles(Image[] files) {
        List<URL> urls = new ArrayList<URL>();

        for (Image image : files) {
            urls.add(getThumbUrlForFile(image));
        }

        return urls;
    }

    private URL getUrlForFile(Image file) {
        String extension = getFileExtension(file);

        try {
            return new URL(mServerFileRequestUrl + file.getFilehash() + "." + extension);
        }
        catch (MalformedURLException e) {
            return null;
        }
    }

    private URL getThumbUrlForFile(Image file) {
        String mimeType = file.getMime();
        String extension = getFileExtension(file);

        URL result = null;

        try {
            if (mimeType.contains("audio") || mimeType.contains("video")) {
                String thumbUrl;
                if (mimeType.contains("audio")) {
                    thumbUrl = mServerThumbRequestUrl + "music.png";
                }
                else {
                    thumbUrl = mServerThumbRequestUrl + "video.png";
                }

                result = new URL(thumbUrl);
            }
            else {
                String thumbUrl;

                if (extension.equals("undefined")) {
                    thumbUrl = mServerThumbRequestUrl + "temp_thumb.jpg";
                }
                else if (extension.equals("gif")) {
                    // GIFs don't have thumbs
                    thumbUrl = mServerFileRequestUrl + file.getFilehash() + "." + extension;
                }
                else {
                    thumbUrl = mServerThumbRequestUrl + file.getFilehash() + "_thumb.jpg";
                }

                result = new URL(thumbUrl);
            }
        }
        catch (MalformedURLException e) {
            Log.e(TAG, "Couldn't parse URL for image " + file.getFilehash() + "." + extension);
        }

        return result;
    }

    private String getFileExtension(Image file) {
        String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(file.getMime());
        if (extension == null) {
            extension = "undefined";
        }

        return extension;
    }

    private void displayImage(int index) {
        Static imageStatic;
        try {
            imageStatic = mBooruStatics.get(index);
        }
        catch (IndexOutOfBoundsException e) {
            mImageIndex = mBooruStatics.size() - 1;
            return;
        }

        Image image = mDatastore.selectImage(KeyPredicate.defaultPredicate().where(
                "filehash == " + imageStatic.getSHA1Hash()))[0];

        String extension = getFileExtension(image);
        if (extension == "gif") {
            mImageView
                    .loadUrl("file://"
                            + mDatastore.pathForStatic(imageStatic, ORMDownloadParameters.Original) + "."
                            + extension);
        }
        else {
            mImageView.loadUrl("file://"
                    + mDatastore.pathForStatic(imageStatic, ORMDownloadParameters.Original) + "_thumb.jpg"); // NOTE Thumbnails always have a ".jpg" extension
        }

        URL url = getUrlForFile(image);

        mLaunchIntent.setDataAndType(Uri.parse(url.toString()), image.getMime());
    }

    /**
     * Queries the nodebooru service for files, ordered by uploaded date, descending
     * 
     * @param number
     *            The number of files to retrieve
     * @param offset
     *            The number of files to skip
     * @param runAfterDownload
     *            If not null, this Runnable will be activated after the download completes
     */
    private void downloadFiles(int number, final int offset, final Runnable runAfterDownload) {
        mDatastore.externalQueryImage(
                KeyPredicate.defaultPredicate().orderBy("uploadedDate", true).limit(number).offset(offset),
                null, new AbstractNetworkCallbacks() {
                    @Override
                    public void onReceivedImage(ORMDatastore ds, String queryName, final Image[] images) {
                        if (images.length > 0) {
                            // Convert list of URLs to array
                            final URL[] urls = getThumbUrlsForFiles(images).toArray(new URL[images.length]);

                            runOnUiThread(new Runnable() {
                                public void run() {
                                    // Setup progress dialog
                                    ProgressDialog dialog = new ProgressDialog(MainActivity.this);
                                    dialog.setTitle(R.string.downloading);
                                    dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                                    dialog.setCancelable(false);

                                    // Download files
                                    new DownloadFilesTask(mDataDirectory, false,
                                            new OnResultListener<List<File>>() {
                                                public void onTaskResult(List<File> files) {
                                                    // Create Statics for the downloaded files
                                                    new CreateStaticsForFilesTask(mDatastore,
                                                            new OnResultListener<List<Static>>() {
                                                                public void onTaskResult(List<Static> files) {
                                                                    // Make sure the files appear in order
                                                                    if (offset >= mBooruStatics.size()) {
                                                                        // Add the files to the end of the list
                                                                        mBooruStatics.addAll(files);
                                                                    }
                                                                    else {
                                                                        // Insert the files at their offset
                                                                        int i = offset;
                                                                        for (Static file : files) {
                                                                            mBooruStatics.add(i, file);
                                                                            i++;
                                                                        }
                                                                    }

                                                                    if (runAfterDownload != null) {
                                                                        runAfterDownload.run();
                                                                    }
                                                                }
                                                            }, new ProgressDialog(MainActivity.this))
                                                            .execute(files.toArray(new File[files.size()]));
                                                }
                                            }, dialog).execute(urls);
                                }
                            });
                        }
                        else {
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    Toast.makeText(MainActivity.this, "No images", Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    }
                });
    }
}
