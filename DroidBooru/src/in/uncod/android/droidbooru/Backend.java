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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

    /**
     * Creates a temporary file containing the contents of the file at the given URI
     * 
     * @param uri
     *            A URI describing the file's location
     * @param resolver
     *            A content resolver (may be null if you're positive the file isn't at a content:// URI
     * 
     * @return The temporary file, or null if the file's contents couldn't be retrieved
     */
    public File createTempFileForUri(Uri uri, ContentResolver resolver) {
        File retFile = null;

        if (resolver != null && uri.getScheme().equals(ContentResolver.SCHEME_CONTENT)) {
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

    /**
     * Creates a temporary file containing the contents of the file at the given HTTP link
     * 
     * @param uri
     *            A URI describing the file's location
     * @param callback
     *            A callback to activate when the file as been downloaded
     * @param dialog
     *            An optional progress dialog
     * 
     * @throws MalformedURLException
     *             If the given URI is invalid
     */
    public void downloadTempFileFromHttp(final Uri uri, OnTaskResultListener<List<File>> callback,
            ProgressDialog dialog) throws MalformedURLException {
        new DownloadFilesTask(mCacheDirectory, false, callback, dialog).execute(new URL(uri.toString()));
    }

    /**
     * Downloads a list of files, using their 'actual' URLs (as opposed to thumbnails)
     * 
     * @param files
     *            The list of BooruFiles to download
     * @param callback
     *            A callback to activate once the download has finished
     * @param dialog
     *            An optional progress dialog
     */
    public void downloadActualFiles(List<BooruFile> files, OnTaskResultListener<List<File>> callback,
            ProgressDialog dialog) {
        URL[] urls = new URL[files.size()];

        // Get the file URLs
        int i = 0;
        for (BooruFile file : files) {
            urls[i] = file.getActualUrl();
            i++;
        }

        new DownloadFilesTask(mCacheDirectory, false, callback, dialog).execute(urls);
    }

    /**
     * Creates a temporary zip file containing the given files
     * 
     * @param files
     *            A list of BooruFiles. These files will be downloaded via their 'actual' URL.
     * @param callback
     *            A callback to activate once the download has finished. The zip file will be the only file in the
     *            result list.
     * @param dialog
     *            An optional progress dialog
     */
    public void downloadAndZipFiles(List<BooruFile> files, final OnTaskResultListener<List<File>> callback,
            ProgressDialog dialog) {
        downloadActualFiles(files, new OnTaskResultListener<List<File>>() {
            public void onTaskResult(List<File> result) {
                // Create zip file
                File zipFile = null;
                try {
                    zipFile = File.createTempFile("DroidBooru", ".zip", mCacheDirectory);

                    ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipFile));

                    for (File file : result) {
                        if (file == null)
                            continue;

                        ZipEntry entry = new ZipEntry(file.getName());
                        out.putNextEntry(entry);

                        FileInputStream inStream = new FileInputStream(file);
                        byte[] buffer = new byte[4096];
                        while (inStream.read(buffer) != -1) {
                            out.write(buffer);
                        }

                        inStream.close();
                        out.flush();
                    }

                    out.flush();
                    out.close();

                }
                catch (IOException e) {
                    zipFile.delete();

                    e.printStackTrace();
                }

                // Return zip file
                callback.onTaskResult(Arrays.asList(new File[] { zipFile }));
            }
        }, dialog);
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
            writer.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        return file;
    }
}
