package in.uncod.android.droidbooru.fragments;

import in.uncod.android.droidbooru.R;
import in.uncod.android.droidbooru.backend.Backend;
import in.uncod.android.util.threading.TaskWithResultListener.OnTaskResultListener;

import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.MenuItem.OnMenuItemClickListener;

/**
 * GallerySelectionHandler handles the selection of items while in an ActionMode
 */
public class GallerySelectionHandler implements ActionMode.Callback {
    private final GalleryFragment galleryFrag;

    public GallerySelectionHandler(GalleryFragment galleryFragment) {
        galleryFrag = galleryFragment;
    }

    private class GetFilesContentClickListener implements OnMenuItemClickListener {
        public boolean onMenuItemClick(com.actionbarsherlock.view.MenuItem item) {
            if (galleryFrag.mSelectedItems.size() > 1) {
                // Zip up all selected files and send that to the requesting app
                Backend.getInstance(galleryFrag.getActivity()).downloadAndZipFilesToCache(
                        galleryFrag.mSelectedItems, new OnTaskResultListener<List<File>>() {
                            public void onTaskResult(List<File> result) {
                                File zipFile = result.get(0);
                                if (zipFile != null) {
                                    galleryFrag.mContainer.onSelectedFiles(new Intent().setData(Uri
                                            .fromFile(zipFile)));
                                }
                                else {
                                    Toast.makeText(galleryFrag.getActivity(), R.string.could_not_get_file,
                                            Toast.LENGTH_LONG).show();

                                    galleryFrag.mActionMode.finish();
                                }
                            }
                        });
            }
            else if (galleryFrag.mSelectedItems.size() > 0) {
                try {
                    // Download the selected file
                    Backend.getInstance(galleryFrag.getActivity()).downloadTempFileFromHttp(
                            Uri.parse(galleryFrag.mSelectedItems.get(0).getActualUrl().toString()),
                            new OnTaskResultListener<List<File>>() {
                                public void onTaskResult(List<File> result) {
                                    File tempFile = result.get(0);
                                    if (tempFile != null) {
                                        galleryFrag.mContainer.onSelectedFiles(new Intent().setData(Uri
                                                .fromFile(tempFile)));
                                    }
                                    else {
                                        Toast.makeText(galleryFrag.getActivity(),
                                                R.string.could_not_get_file, Toast.LENGTH_LONG).show();

                                        galleryFrag.mActionMode.finish();
                                    }
                                }
                            });
                }
                catch (MalformedURLException e) {
                    e.printStackTrace();
                }
            }

            return true;
        }
    }

    private class ShareFileLinksClickListener implements OnMenuItemClickListener {
        public boolean onMenuItemClick(com.actionbarsherlock.view.MenuItem item) {
            if (galleryFrag.mSelectedItems.size() > 1) {
                // Sharing multiple links
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_STREAM,
                        Uri.fromFile(galleryFrag.mBackend.createLinkContainer(galleryFrag.mSelectedItems)));
                intent.setType("text/plain");
                galleryFrag.startActivityForResult(
                        Intent.createChooser(intent,
                                galleryFrag.getResources().getString(R.string.share_files_with)),
                        GalleryFragment.REQ_CODE_CHOOSE_SHARING_APP);
            }
            else if (galleryFrag.mSelectedItems.size() == 1) {
                // Sharing a single link
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_TEXT, galleryFrag.mSelectedItems.get(0).getActualUrl()
                        .toString());
                intent.setType("text/plain");
                galleryFrag.startActivityForResult(
                        Intent.createChooser(intent,
                                galleryFrag.getResources().getString(R.string.share_files_with)),
                        GalleryFragment.REQ_CODE_CHOOSE_SHARING_APP);
            }

            galleryFrag.mActionMode.finish();

            return true;
        }
    }

    private class ShareActualFilesClickListener implements OnMenuItemClickListener {
        public boolean onMenuItemClick(com.actionbarsherlock.view.MenuItem item) {
            if (galleryFrag.mSelectedItems.size() > 1) {
                // Sharing multiple files
                final ArrayList<Uri> uris = new ArrayList<Uri>();

                Backend.getInstance(galleryFrag.getActivity()).downloadActualFilesToCache(
                        galleryFrag.mSelectedItems, new OnTaskResultListener<List<File>>() {
                            public void onTaskResult(List<File> result) {
                                // Get URIs for the files
                                for (File file : result) {
                                    if (file != null)
                                        uris.add(Uri.fromFile(file));
                                }

                                if (uris.size() == 0) {
                                    Toast.makeText(galleryFrag.getActivity(), R.string.could_not_connect,
                                            Toast.LENGTH_LONG).show();
                                    return;
                                }

                                // Send sharing intent
                                Intent intent = new Intent();
                                intent.setAction(Intent.ACTION_SEND_MULTIPLE);
                                intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
                                intent.setType("*/*");
                                galleryFrag.startActivityForResult(
                                        Intent.createChooser(
                                                intent,
                                                galleryFrag.getResources().getString(
                                                        R.string.share_files_with)),
                                        GalleryFragment.REQ_CODE_CHOOSE_SHARING_APP);
                            }
                        });
            }
            else if (galleryFrag.mSelectedItems.size() == 1) {
                // Sharing a single file
                Backend.getInstance(galleryFrag.getActivity()).downloadActualFilesToCache(
                        galleryFrag.mSelectedItems, new OnTaskResultListener<List<File>>() {
                            public void onTaskResult(List<File> result) {
                                if (result.get(0) == null) {
                                    Toast.makeText(galleryFrag.getActivity(), R.string.could_not_get_file,
                                            Toast.LENGTH_LONG).show();
                                    return;
                                }

                                // Send sharing intent
                                Intent intent = new Intent();
                                intent.setAction(Intent.ACTION_SEND);
                                intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(result.get(0)));
                                intent.setType("*/*");
                                galleryFrag.startActivityForResult(
                                        Intent.createChooser(
                                                intent,
                                                galleryFrag.getResources().getString(
                                                        R.string.share_files_with)),
                                        GalleryFragment.REQ_CODE_CHOOSE_SHARING_APP);
                            }
                        });
            }

            galleryFrag.mActionMode.finish();

            return true;
        }
    }

    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        // Setup menu and mode title
        mode.setTitle(R.string.select_files_to_share);

        // Share option
        com.actionbarsherlock.view.MenuItem shareMenu = menu.add(R.string.share);

        String action = galleryFrag.getActivity().getIntent().getAction();
        if (action != null && action.equals(Intent.ACTION_GET_CONTENT)) {
            // User is in the process of selecting file(s) for another application
            shareMenu.setOnMenuItemClickListener(new GetFilesContentClickListener());
        }
        else {
            // Allow sharing of the full-size image to other apps
            shareMenu.setOnMenuItemClickListener(new ShareActualFilesClickListener());

            // Share link(s) option
            menu.add(R.string.share_link).setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                    .setOnMenuItemClickListener(new ShareFileLinksClickListener());
        }

        return true;
    }

    public void onDestroyActionMode(ActionMode mode) {
        galleryFrag.mActionMode = null;
        galleryFrag.mSelectedItems.clear();

        galleryFrag.mContainer.onSelectionEnded();
    }

    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        // Update selected item count
        if (galleryFrag.mSelectedItems.size() > 0) {
            mode.setSubtitle(galleryFrag.mSelectedItems.size()
                    + galleryFrag.getResources().getString(R.string.items_selected));
        }
        else {
            mode.setSubtitle("");
        }

        return true;
    }

    public boolean onActionItemClicked(ActionMode mode, com.actionbarsherlock.view.MenuItem item) {
        return false;
    }
}