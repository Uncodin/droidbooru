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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.MimeTypeMap;
import android.webkit.WebView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockActivity;

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
            downloadImages(5, new Runnable() {
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

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mDataDirectory = getExternalFilesDir(null);
        Log.d(TAG, "Using data directory: " + mDataDirectory.getAbsolutePath());

        mControls = findViewById(R.id.controls);

        mConnectButton = findViewById(R.id.button_connect);
        mConnectButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                mConnectButton.setEnabled(false);

                mDatastore = ORMDatastore.create(new File(mDataDirectory, "droidbooru.db").getAbsolutePath());
                mDatastore.setDownloadPathPrefix(mDataDirectory.getAbsolutePath() + File.separator);
                mDatastore.setNetworkHandler(new HttpClientNetwork("http://img.uncod.in/v2/api"));
                mDatastore.authenticate("test", "test", new AuthCallback());
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
                    downloadImages(5, new Runnable() {
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
            return new URL("http://img.uncod.in/img/" + file.getFilehash() + "." + extension);
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
                    thumbUrl = "http://img.uncod.in/thumb/music.png";
                }
                else {
                    thumbUrl = "http://img.uncod.in/thumb/music.png";
                }

                result = new URL(thumbUrl);
            }
            else {
                String thumbUrl;

                if (extension.equals("undefined")) {
                    thumbUrl = "http://img.uncod.in/thumb/temp_thumb.jpg";
                }
                else if (extension.equals("gif")) {
                    // GIFs don't have thumbs
                    thumbUrl = "http://img.uncod.in/img/" + file.getFilehash() + "." + extension;
                }
                else {
                    thumbUrl = "http://img.uncod.in/thumb/" + file.getFilehash() + "_thumb.jpg";
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

    private void downloadImages(int number, final Runnable runAfterDownload) {
        mDatastore.externalQueryImage(
                KeyPredicate.defaultPredicate().orderBy("uploadedDate", true).limit(number)
                        .offset(mBooruStatics.size()), null, new AbstractNetworkCallbacks() {
                    @Override
                    public void onReceivedImage(ORMDatastore ds, String queryName, final Image[] images) {
                        if (images.length > 0) {
                            // Convert list of URLs to array
                            final URL[] urls = getThumbUrlsForFiles(images).toArray(new URL[0]);

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
                                                                    mBooruStatics.addAll(files);

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
