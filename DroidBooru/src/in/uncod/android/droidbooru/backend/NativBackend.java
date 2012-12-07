package in.uncod.android.droidbooru.backend;

import in.uncod.android.droidbooru.BooruFile;
import in.uncod.android.droidbooru.net.FilesDownloadedCallback;
import in.uncod.android.droidbooru.net.FilesUploadedCallback;
import in.uncod.android.net.IConnectivityStatus;
import in.uncod.nativ.AbstractNetworkCallbacks;
import in.uncod.nativ.HttpClientNetwork;
import in.uncod.nativ.INetworkHandler;
import in.uncod.nativ.Image;
import in.uncod.nativ.KeyPredicate;
import in.uncod.nativ.ORMDatastore;

import java.io.File;
import java.net.MalformedURLException;

import android.app.ProgressDialog;
import android.os.Handler;
import android.util.Log;

class NativBackend extends Backend {
    class AuthCallback extends AbstractNetworkCallbacks {
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

    ORMDatastore mDatastore;

    NativBackend(File dataDirectory, File cacheDirectory, String serverAddress,
            IConnectivityStatus connectionChecker) {
        super(dataDirectory, cacheDirectory, serverAddress, connectionChecker);

        TAG = "NativBackend";

        mDatastore = ORMDatastore.create(new File(mCacheDirectory, "droidbooru.db").getAbsolutePath());
        mDatastore.setDownloadPathPrefix(mDataDirectory.getAbsolutePath() + File.separator);
        mDatastore.setNetworkHandler(new HttpClientNetwork(mServerApiUrl.toString()));
    }

    @Override
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

    @Override
    public boolean uploadFiles(final File[] files, final String email, final String tags,
            final Handler uiHandler, final FilesUploadedCallback callback, final ProgressDialog dialog) {
        if (!mConnectionChecker.canConnectToNetwork())
            return false;

        if (!mDatastore.hasAuthenticationToken()) {
            mDatastore.authenticate("test", "test", new AuthCallback() {
                public void finished(String extras) {
                    if (!mError) {
                        doFileUpload(files, email, tags, uiHandler, callback, dialog);
                    }
                    else {
                        callback.onFilesUploaded(true);
                    }
                }
            });
        }
        else {
            doFileUpload(files, email, tags, uiHandler, callback, dialog);
        }

        return true;
    }

    @Override
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

    @Override
    public boolean queryExternalFiles(final int number, final int offset,
            final FilesDownloadedCallback callback) {
        if (!mConnectionChecker.canConnectToNetwork())
            return false;

        if (!mDatastore.hasAuthenticationToken()) {
            mDatastore.authenticate("test", "test", new AuthCallback() {
                public void finished(String extras) {
                    if (!mError) {
                        queryExternalFilesAfterAuth(number, offset, callback);
                    }
                }
            });
        }
        else {
            queryExternalFilesAfterAuth(number, offset, callback);
        }

        return true;
    }

    private BooruFile[] createBooruFilesForFiles(Image[] files) {
        BooruFile[] bFiles = new BooruFile[files.length];

        try {
            int i = 0;
            for (Image file : files) {
                bFiles[i] = BooruFile.create(mDatastore.getDownloadPathPrefix(), file.getMime(),
                        file.getFilehash(), file.getPid().toString(), mServerThumbRequestUrl.toURL(),
                        mServerFileRequestUrl.toURL());

                i++;
            }
        }
        catch (MalformedURLException e) {
            Log.e(TAG, "Invalid URL, check the server address");
        }

        return bFiles;
    }

    private void queryExternalFilesAfterAuth(final int number, final int offset,
            final FilesDownloadedCallback callback) {
        mDatastore.externalQueryImage(
                KeyPredicate.defaultPredicate().orderBy("uploadedDate", true).limit(number).offset(offset),
                null, new AbstractNetworkCallbacks() {
                    public void onReceivedImage(ORMDatastore ds, String queryName, Image[] d) {
                        callback.onFilesDownloaded(offset, createBooruFilesForFiles(d));
                    };
                });
    }
}
