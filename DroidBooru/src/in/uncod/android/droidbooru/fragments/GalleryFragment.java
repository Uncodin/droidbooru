package in.uncod.android.droidbooru.fragments;

import in.uncod.android.droidbooru.BooruFile;
import in.uncod.android.droidbooru.R;
import in.uncod.android.droidbooru.backend.Backend;
import in.uncod.android.droidbooru.backend.Backend.BackendConnectedCallback;
import in.uncod.android.droidbooru.net.FilesDownloadedCallback;
import in.uncod.android.droidbooru.net.FilesUploadedCallback;
import in.uncod.android.graphics.BitmapManager;
import in.uncod.android.graphics.BitmapManager.OnBitmapLoadedListener;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import android.accounts.Account;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.MenuItem.OnMenuItemClickListener;

public class GalleryFragment extends SherlockFragment {
    private static final String TAG = "GalleryFragment";

    private View mRootView;

    /**
     * Request code for choosing a file to upload
     */
    private static final int REQ_CODE_CHOOSE_FILE_UPLOAD = 0;

    /**
     * Request code for choosing which app to share files with
     */
    static final int REQ_CODE_CHOOSE_SHARING_APP = 1;

    /**
     * The number of files to populate the gallery with on the initial request
     */
    protected static final int NUM_FILES_INITIAL_DOWNLOAD = 20;

    /**
     * The number of files to download when scrolling near the end of the gallery
     */
    private static final int NUM_FILES_DOWNLOAD_WHILE_SCROLLING = 5;

    Backend mBackend;

    private GridView mGridView;
    private ArrayAdapter<BooruFile> mBooruFileAdapter;

    private Intent mLaunchIntent;
    private boolean mDownloadWhileScrolling = true;

    private Handler mUiHandler;
    ActionMode mActionMode;
    List<BooruFile> mSelectedItems = new ArrayList<BooruFile>();

    private BitmapManager mBitmapManager;

    IGalleryContainer mContainer;

    protected boolean mSelecting;

    /**
     * This interface must be implemented by Activities that contain a GalleryFragment
     */
    public interface IGalleryContainer {
        /**
         * @return the Account used to connect to the nodebooru server
         */
        Account getAccount();

        /**
         * Called when files have been requested from the gallery
         * 
         * @param data
         *            An Intent containing a URI to either a single selected file or a zip file containing multiple
         *            selected file
         */
        void onSelectedFiles(Intent data);

        /**
         * Called when file selection ended without a selected file, either due to cancellation or an error
         */
        void onSelectionEnded();
    }

    private class UpdateDisplayedFilesCallback implements FilesDownloadedCallback {
        public UpdateDisplayedFilesCallback() {
            getActivity().setProgressBarIndeterminateVisibility(true); // Show progress indicator
        }

        public void onFilesDownloaded(int offset, BooruFile[] bFiles) {
            if (bFiles.length > 0) {
                int i = Math.min(mBooruFileAdapter.getCount(), offset);
                for (BooruFile file : bFiles) {
                    mBooruFileAdapter.insert(file, i); // addAll() is API level 11
                    i++;
                }
            }

            getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    getActivity().setProgressBarIndeterminateVisibility(false); // Hide progress indicator
                }
            });
        }
    }

    @Override
    public void onAttach(Activity act) {
        super.onAttach(act);

        mContainer = (IGalleryContainer) act;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        setRetainInstance(true);
        setHasOptionsMenu(true);

        mRootView = inflater.inflate(R.layout.fragment_gallery, container);

        mUiHandler = new Handler();

        mBitmapManager = BitmapManager.get(getActivity(), .5);

        initializeUI();

        return mRootView;
    }

    private void initializeUI() {
        mLaunchIntent = new Intent();
        mLaunchIntent.setAction(android.content.Intent.ACTION_VIEW);

        if (mBooruFileAdapter == null) {
            mBooruFileAdapter = new ArrayAdapter<BooruFile>(getActivity(), 0) {
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    return displayThumbInView(convertView, mBooruFileAdapter.getItem(position));
                }
            };
        }

        mGridView = (GridView) mRootView.findViewById(R.id.images);
        mGridView.setAdapter(mBooruFileAdapter);
        mGridView.setEmptyView(mRootView.findViewById(android.R.id.empty));

        mGridView.setOnItemLongClickListener(new OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (mActionMode == null) {
                    mActionMode = getSherlockActivity().startActionMode(
                            new GallerySelectionHandler(GalleryFragment.this));
                }

                return false;
            }
        });

        mGridView.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (mActionMode == null) {
                    // Launch a viewer for the file
                    try {
                        BooruFile bFile = mBooruFileAdapter.getItem(position);

                        mLaunchIntent.setDataAndType(Uri.parse(bFile.getActualUrl().toString()),
                                bFile.getMimeForLaunch());

                        startActivity(mLaunchIntent);
                    }
                    catch (ActivityNotFoundException e) {
                        e.printStackTrace();

                        Toast.makeText(getActivity(), "Sorry, your device can't view the original file!",
                                Toast.LENGTH_LONG).show();
                    }
                }
                else {
                    updateSelectedFiles(position);
                }
            }
        });

        mGridView.setOnScrollListener(new OnScrollListener() {
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                // Unused for now
            }

            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                    int totalItemCount) {
                if (mDownloadWhileScrolling
                        && totalItemCount > 0
                        && totalItemCount - (firstVisibleItem + visibleItemCount) <= NUM_FILES_INITIAL_DOWNLOAD) {
                    mDownloadWhileScrolling = false;

                    // User only has three pages of items left to scroll through; load more
                    mBackend.downloadFiles(NUM_FILES_DOWNLOAD_WHILE_SCROLLING, mBooruFileAdapter.getCount(),
                            mUiHandler, new UpdateDisplayedFilesCallback() {
                                @Override
                                public void onFilesDownloaded(int offset, BooruFile[] bFiles) {
                                    super.onFilesDownloaded(offset, bFiles);

                                    if (bFiles.length == 0) {
                                        // If we don't get anything back, assume we're at the end of the list
                                        mDownloadWhileScrolling = false;
                                    }
                                    else {
                                        mDownloadWhileScrolling = true;
                                    }
                                }
                            });
                }
            }
        });
    }

    public void load() {
        if (mBackend != null) {
            if (!mDownloadWhileScrolling) {
                // If this flag isn't set, we should be in the middle of a download
                getActivity().setProgressBarIndeterminateVisibility(true);
            }

            return; // No need to load if the Backend has been set before
        }

        mBackend = Backend.getInstance(getActivity());
        if (!mBackend.connect(new BackendConnectedCallback() {
            public void onBackendConnected(boolean error) {
                if (error) {
                    getActivity().runOnUiThread(new Runnable() {
                        public void run() {
                            Toast.makeText(getActivity(), R.string.could_not_connect, Toast.LENGTH_LONG)
                                    .show();
                        }
                    });
                }
                else {
                    getActivity().runOnUiThread(new Runnable() {
                        public void run() {
                            mBackend.downloadFiles(NUM_FILES_INITIAL_DOWNLOAD, 0, mUiHandler,
                                    new UpdateDisplayedFilesCallback());
                        }
                    });
                }
            }
        })) {
            // Not connected to the network
            Toast.makeText(getActivity(), R.string.network_disconnected, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // Upload
        menu.add(R.string.upload).setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
                .setOnMenuItemClickListener(new OnMenuItemClickListener() {
                    public boolean onMenuItemClick(com.actionbarsherlock.view.MenuItem item) {
                        showFileChooser();

                        return true;
                    }
                });

        // Refresh
        menu.add(R.string.refresh).setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_NEVER)
                .setOnMenuItemClickListener(new OnMenuItemClickListener() {
                    public boolean onMenuItemClick(com.actionbarsherlock.view.MenuItem item) {
                        mBooruFileAdapter.clear();
                        mDownloadWhileScrolling = true;

                        Backend.getInstance(getActivity()).downloadFiles(NUM_FILES_INITIAL_DOWNLOAD, 0,
                                mUiHandler, new UpdateDisplayedFilesCallback());

                        return true;
                    }
                });
    }

    private void showFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        startActivityForResult(
                Intent.createChooser(intent, getResources().getString(R.string.upload_file_from)),
                REQ_CODE_CHOOSE_FILE_UPLOAD);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
        case REQ_CODE_CHOOSE_FILE_UPLOAD:
            if (resultCode == Activity.RESULT_OK) {
                // Upload chosen file
                uploadChosenFile(data);
            }

            break;
        case REQ_CODE_CHOOSE_SHARING_APP:
            if (resultCode == Activity.RESULT_OK) {
                // Finished sharing; clear action mode
                mActionMode.finish();
                mActionMode = null;
            }

            break;
        default:
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void uploadChosenFile(Intent data) {
        final Uri uri = data.getData();
        Log.d(TAG, "Upload request for " + uri);

        new AsyncTask<Void, Void, File>() {
            @Override
            protected File doInBackground(Void... params) {
                return mBackend.createTempFileForUri(uri, getActivity().getContentResolver());
            }

            protected void onPostExecute(File uploadFile) {
                if (uploadFile != null && uploadFile.exists()) {
                    mBackend.uploadFiles(new File[] { uploadFile }, mContainer.getAccount().name,
                            mBackend.getDefaultTags(), mUiHandler, new FilesUploadedCallback() {
                                public void onFilesUploaded(final boolean error) {
                                    getActivity().runOnUiThread(new Runnable() {
                                        public void run() {
                                            if (!error) {
                                                // Download and display the image that was just uploaded
                                                mBackend.downloadFiles(1, 0, mUiHandler,
                                                        new UpdateDisplayedFilesCallback());
                                            }
                                            else {
                                                Toast.makeText(getActivity(), R.string.upload_failed,
                                                        Toast.LENGTH_LONG).show();
                                            }
                                        }
                                    });
                                }
                            });
                }
                else {
                    Toast.makeText(getActivity(), R.string.upload_failed, Toast.LENGTH_LONG).show();
                }
            };
        }.execute((Void) null);
    }

    private View displayThumbInView(View convertView, BooruFile booruFile) {
        FrameLayout layout;

        // Determine if we can reuse the view
        if (convertView == null) {
            layout = (FrameLayout) LayoutInflater.from(getActivity()).inflate(
                    R.layout.view_gallery_thumbnail, null);
        }
        else {
            layout = (FrameLayout) convertView;
        }

        final ImageView image = (ImageView) layout.findViewById(R.id.thumbnail_image);

        // Load image
        File imageFile = booruFile.getThumbPath();
        if (imageFile != null) {
            mBitmapManager.displayBitmapScaled(imageFile.getAbsolutePath(), image, -1,
                    new OnBitmapLoadedListener() {
                        public void beforeImageLoaded(boolean cached) {
                            if (!cached) {
                                getActivity().runOnUiThread(new Runnable() {
                                    public void run() {
                                        image.setImageBitmap(null);
                                    }
                                });
                            }
                        }

                        public void onImageLoaded(boolean cached) {
                            if (!cached) {
                                getActivity().runOnUiThread(new Runnable() {
                                    public void run() {
                                        image.startAnimation(AnimationUtils.loadAnimation(getActivity(),
                                                R.anim.fadein));
                                    }
                                });
                            }
                        }
                    });
        }

        return layout;
    }

    private void updateSelectedFiles(int position) {
        // Add/remove in list of selected items
        BooruFile file = mBooruFileAdapter.getItem(position);

        if (mSelectedItems.contains(file)) {
            mSelectedItems.remove(file);
        }
        else {
            mSelectedItems.add(file);
        }

        mActionMode.invalidate();
    }

    public void beginSelection() {
        mActionMode = getSherlockActivity()
                .startActionMode(new GallerySelectionHandler(GalleryFragment.this));
    }
}
