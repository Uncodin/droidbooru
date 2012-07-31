package in.uncod.android.droidbooru;

import in.uncod.android.net.DownloadFilesTask;
import in.uncod.android.util.threading.TaskWithResultListener.OnTaskResultListener;
import in.uncod.nativ.AbstractNetworkCallbacks;
import in.uncod.nativ.HttpClientNetwork;
import in.uncod.nativ.INetworkHandler;
import in.uncod.nativ.Image;
import in.uncod.nativ.KeyPredicate;
import in.uncod.nativ.ORMDatastore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;

import org.apache.http.HttpHost;

import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;

public class Backend {
    private class AuthCallback extends AbstractNetworkCallbacks {
        protected boolean mError;
        protected int mErrorCode;
        protected String mErrorMessage;

        @Override
        public void onError(INetworkHandler handler, int errorCode, String message) {
            super.onError(handler, errorCode, message);

            mError = true;
            mErrorCode = errorCode;
            mErrorMessage = message;
        }
    }

    private static final String TAG = null;

    private static Backend mInstance;

    private ORMDatastore mDatastore;

    private File mDataDirectory;
    private File mCacheDirectory;

    private URI mServerAddress;
    private URI mServerNativApiUrl;
    private URI mServerFilePostUrl;
    private URI mServerFileRequestUrl;
    private URI mServerThumbRequestUrl;

    private IConnectivityStatus mConnectionChecker;

    public static Backend init(File dataDirectory, File cacheDirectory, String serverAddress,
            IConnectivityStatus connectionChecker) {
        mInstance = new Backend(dataDirectory, cacheDirectory, serverAddress, connectionChecker);

        return mInstance;
    }

    public static Backend getInstance() {
        if (mInstance == null)
            throw new IllegalStateException("Backend has not been initialized; call init() first");

        return mInstance;
    }

    private Backend(File dataDirectory, File cacheDirectory, String serverAddress,
            IConnectivityStatus connectionChecker) {
        mConnectionChecker = connectionChecker;

        // Get server address
        mServerAddress = URI.create(HttpHost.DEFAULT_SCHEME_NAME + "://" + serverAddress);

        // Set directories
        mDataDirectory = dataDirectory;
        mCacheDirectory = cacheDirectory;

        // Setup various endpoints
        mServerNativApiUrl = URI.create(mServerAddress + "/v2/api");
        mServerFilePostUrl = URI.create(mServerAddress + "/upload/curl");
        mServerFileRequestUrl = URI.create(mServerAddress + "/img/");
        mServerThumbRequestUrl = URI.create(mServerAddress + "/thumb/");

        mDatastore = ORMDatastore.create(new File(mCacheDirectory, "droidbooru.db").getAbsolutePath());
        mDatastore.setDownloadPathPrefix(mDataDirectory.getAbsolutePath() + File.separator);
        mDatastore.setNetworkHandler(new HttpClientNetwork(mServerNativApiUrl.toString()));
    }

    public boolean connect(final BackendConnectedCallback callback) {
        if (!mConnectionChecker.canConnectToNetwork())
            return false;

        mDatastore.authenticate("test", "test", new AbstractNetworkCallbacks() {
            private boolean mError;

            @Override
            public void onError(INetworkHandler handler, int errorCode, String message) {
                super.onError(handler, errorCode, message);

                mError = true;
            }

            @Override
            public void finished(String extras) {
                super.finished(extras);

                if (callback != null)
                    callback.onBackendConnected(mError);
            }
        });

        return true;
    }

    public File getFileForUri(Uri uri, ContentResolver resolver) {
        File retFile = null;

        if (uri.getScheme().equals(ContentResolver.SCHEME_CONTENT)) {
            try {
                // Copy the file to our cache
                retFile = File.createTempFile("upload", ".tmp", mCacheDirectory);

                InputStream inStream = resolver.openInputStream(uri);
                FileOutputStream outStream = new FileOutputStream(retFile);

                byte[] buffer = new byte[1024];
                while (inStream.read(buffer) != -1) {
                    outStream.write(buffer);
                }

                inStream.close();

                outStream.flush();
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

    private URL[] getThumbUrlsForBooruFiles(BooruFile[] bFiles) {
        URL[] urls = new URL[bFiles.length];

        int i = 0;
        for (BooruFile bFile : bFiles) {
            urls[i] = bFile.getThumbUrl();
            i++;
        }

        return urls;
    }

    public boolean uploadFiles(final File[] files, final String email, final String tags,
            final Handler uiHandler, final FilesUploadedCallback callback, final ProgressDialog dialog) {
        if (!mConnectionChecker.canConnectToNetwork())
            return false;

        if (!mDatastore.hasAuthenticationToken()) {
            mDatastore.authenticate("test", "test", new AuthCallback() {
                public void finished(String extras) {
                    if (!mError) {
                        internalUploadFiles(files, email, tags, uiHandler, callback, dialog);
                    }
                    else {
                        callback.onFilesUploaded(true);
                    }
                }
            });
        }
        else {
            internalUploadFiles(files, email, tags, uiHandler, callback, dialog);
        }

        return true;
    }

    private void internalUploadFiles(final File[] files, final String email, final String tags,
            Handler uiHandler, final FilesUploadedCallback runWhenFinished, final ProgressDialog dialog) {
        uiHandler.post(new Runnable() {
            public void run() {
                new BooruUploadTask(mServerFilePostUrl, email, tags, new OnTaskResultListener<Boolean>() {
                    public void onTaskResult(Boolean error) {
                        if (runWhenFinished != null) {
                            runWhenFinished.onFilesUploaded(error);
                        }
                    }
                }, dialog).execute(files);
            }
        });
    }

    public String getDefaultTags() {
        return "droidbooru," + new SimpleDateFormat("MM-dd-yy").format(Calendar.getInstance().getTime());
    }

    /**
     * Queries the nodebooru service for files, ordered by uploaded date, descending
     * 
     * @param number
     *            The number of files to retrieve
     * @param offset
     *            The number of files to skip
     * @return
     */
    public boolean downloadFiles(final int number, final int offset, final Handler uiHandler,
            final ProgressDialog dialog, final FilesDownloadedCallback callback) {
        if (!mConnectionChecker.canConnectToNetwork())
            return false;

        if (!mDatastore.hasAuthenticationToken()) {
            mDatastore.authenticate("test", "test", new AuthCallback() {
                @Override
                public void finished(String extras) {
                    super.finished(extras);

                    if (!mError) {
                        queryExternalAndDownload(number, offset, uiHandler, dialog, callback);
                    }
                    else {
                        callback.onFilesDownloaded(offset, new BooruFile[0]);
                    }
                }
            });
        }
        else {
            queryExternalAndDownload(number, offset, uiHandler, dialog, callback);
        }

        return true;
    }

    private void queryExternalAndDownload(int number, final int offset, final Handler uiHandler,
            final ProgressDialog dialog, final FilesDownloadedCallback callback) {
        Log.d(TAG, "Downloading " + number + " files from offset " + offset);
        mDatastore.externalQueryImage(
                KeyPredicate.defaultPredicate().orderBy("uploadedDate", true).limit(number).offset(offset),
                null, new AbstractNetworkCallbacks() {
                    @Override
                    public void onReceivedImage(ORMDatastore ds, String queryName, final Image[] files) {
                        if (files.length > 0) {
                            final BooruFile[] bFiles = createBooruFilesForFiles(files);

                            uiHandler.post(new Runnable() {

                                public void run() {
                                    // Download thumbnails
                                    new DownloadFilesTask(mDataDirectory, false,
                                            new OnTaskResultListener<List<File>>() {
                                                public void onTaskResult(List<File> dFiles) {
                                                    // Associate the files with each BooruFile
                                                    int i = 0;
                                                    for (File file : dFiles) {
                                                        bFiles[i].setThumbFile(file);
                                                        i++;
                                                    }

                                                    if (callback != null)
                                                        callback.onFilesDownloaded(offset, bFiles);
                                                }
                                            }, dialog).execute(getThumbUrlsForBooruFiles(bFiles));
                                }
                            });
                        }
                        else {
                            if (callback != null)
                                callback.onFilesDownloaded(offset, new BooruFile[0]);
                        }
                    }
                });
    }

    public BooruFile[] createBooruFilesForFiles(Image[] files) {
        BooruFile[] bFiles = new BooruFile[files.length];

        try {
            int i = 0;
            for (Image file : files) {
                bFiles[i] = BooruFile.create(mDatastore, file, mServerThumbRequestUrl.toURL(),
                        mServerFileRequestUrl.toURL());

                i++;
            }
        }
        catch (MalformedURLException e) {
            Log.e(TAG, "Invalid URL, check the server address");
        }

        return bFiles;
    }

    /**
     * Creates a text file containing HTTP links to the given files
     * 
     * @param files
     *            The files whose links will be included
     * 
     * @return A File pointing to the text file containing the links
     */
    public File createLinkContainer(List<BooruFile> files) {
        File file = null;
        try {
            file = File.createTempFile("links", ".txt", mCacheDirectory);

            FileWriter writer = new FileWriter(file);

            for (BooruFile bFile : files) {
                writer.write(bFile.getActualUrl().toString() + "\n");
            }

            writer.flush();
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        return file;
    }
}
