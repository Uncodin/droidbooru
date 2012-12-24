package in.uncod.android.droidbooru.backend;

import in.uncod.android.droidbooru.BooruFile;
import in.uncod.android.droidbooru.net.BooruUploadTask;
import in.uncod.android.droidbooru.net.FilesDownloadedCallback;
import in.uncod.android.droidbooru.net.FilesUploadedCallback;
import in.uncod.android.net.DownloadFilesTask;
import in.uncod.android.net.IConnectivityStatus;
import in.uncod.android.util.threading.TaskWithResultListener.OnTaskResultListener;

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
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

public abstract class Backend {
    public interface BackendConnectedCallback {
        void onBackendConnected(boolean error);
    }

    protected static String TAG = "Backend";
    private static Backend mInstance;
    protected File mDataDirectory;
    protected File mCacheDirectory;
    protected URI mServerAddress;
    protected URI mServerApiUrl;
    protected URI mServerFilePostUrl;
    protected URI mServerFileRequestUrl;
    protected URI mServerThumbRequestUrl;
    protected IConnectivityStatus mConnectionChecker;

    public static Backend init(Context ctx, File dataDirectory, File cacheDirectory, String serverAddress,
            IConnectivityStatus connectionChecker) {
        // Determine which Backend we need
        if (Build.DEVICE.contains("limo")) {
            mInstance = new MODLiveBackend(ctx, dataDirectory, cacheDirectory, serverAddress,
                    connectionChecker);
        }
        else if (ctx.getPackageManager().hasSystemFeature("com.google.android.tv")) {
            mInstance = new SimpleHTTPBackend(dataDirectory, cacheDirectory, serverAddress, connectionChecker);
        }
        else {
            mInstance = new NativBackend(dataDirectory, cacheDirectory, serverAddress, connectionChecker);
        }

        return mInstance;
    }

    public static Backend getInstance() {
        if (mInstance == null)
            throw new IllegalStateException("Backend has not been initialized; call init() first");

        return mInstance;
    }

    Backend(File dataDirectory, File cacheDirectory, String serverAddress,
            IConnectivityStatus connectionChecker) {
        mConnectionChecker = connectionChecker;

        // Get server address
        mServerAddress = URI.create(HttpHost.DEFAULT_SCHEME_NAME + "://" + serverAddress);

        // Set directories
        mDataDirectory = dataDirectory;
        mCacheDirectory = cacheDirectory;

        // Setup various endpoints
        mServerApiUrl = URI.create(mServerAddress + "/v2/api");
        mServerFilePostUrl = URI.create(mServerAddress + "/upload/curl");
        mServerFileRequestUrl = URI.create(mServerAddress + "/img/");
        mServerThumbRequestUrl = URI.create(mServerAddress + "/thumb/");
    }

    /**
     * Authenticates with the nodebooru service if possible
     * 
     * @param callback
     *            If not null, this callback will be activated after the authentication attempt
     * 
     * @return true if a network connection was available at the time of the attempt
     */
    public abstract boolean connect(final BackendConnectedCallback callback);

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
    public void downloadActualFilesToCache(List<BooruFile> files, OnTaskResultListener<List<File>> callback,
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
    public void downloadAndZipFilesToCache(List<BooruFile> files,
            final OnTaskResultListener<List<File>> callback, ProgressDialog dialog) {
        downloadActualFilesToCache(files, new OnTaskResultListener<List<File>>() {
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

    URL[] getThumbUrlsForBooruFiles(BooruFile[] bFiles) {
        URL[] urls = new URL[bFiles.length];

        int i = 0;
        for (BooruFile bFile : bFiles) {
            urls[i] = bFile.getThumbUrl();
            i++;
        }

        return urls;
    }

    /**
     * Upload the given files to a nodebooru service
     * 
     * @param files
     *            An array of Files to upload
     * @param email
     *            The email address of the uploading user
     * @param tags
     *            The tags to apply to each uploaded file
     * @param uiHandler
     *            A Handler instance attached to a UI thread; an async task will be started on this thread
     * @param callback
     *            If not null, this callback will be activated after the files are uploaded
     * @param dialog
     *            If not null, this dialog will be automatically updated with the upload progress & dismissed
     * 
     * @return true if a connection to the network is available at the time of the upload request
     */
    public abstract boolean uploadFiles(final File[] files, final String email, final String tags,
            final Handler uiHandler, final FilesUploadedCallback callback, final ProgressDialog dialog);

    protected void doFileUpload(final File[] files, final String email, final String tags, Handler uiHandler,
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
    }

    /**
     * Gets the default tags applied to every uploaded file
     * 
     * @return A comma-delimited list of tags
     */
    public String getDefaultTags() {
        return "droidbooru," + new SimpleDateFormat("MM-dd-yy").format(Calendar.getInstance().getTime());
    }

    /**
     * Downloads files from the nodebooru service as a list, ordered by uploaded date, descending
     * 
     * @param number
     *            The number of files to retrieve
     * @param offset
     *            The number of files to skip
     * 
     * @return true if a connection to the service was available
     */
    public abstract boolean downloadFiles(final int number, final int offset, final Handler uiHandler,
            final ProgressDialog dialog, final FilesDownloadedCallback callback);

    /**
     * Queries the nodebooru service for a list of files, ordered by uploaded date, descending
     * 
     * @param number
     *            The number of files to retrieve
     * 
     * @param offset
     *            The number of files to skip
     * 
     * @param callback
     *            A callback that will be activated when files have been received
     * 
     * @return true if a connection to the service was available
     */
    public abstract boolean queryExternalFiles(final int number, final int offset,
            final FilesDownloadedCallback callback);

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

    /**
     * After querying for file locations, starts a background task to download the files to the device
     * 
     * @param number
     *            The number of files to download
     * @param offset
     *            The number of files to skip
     * @param uiHandler
     *            A Handler on the UI thread
     * @param dialog
     *            If not null, this dialog will be shown to report progress
     * @param callback
     *            If not null, this callback will be activated after the files are downloaded
     */
    protected void queryExternalAndDownload(int number, final int offset, final Handler uiHandler,
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
    }

}