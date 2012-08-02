package in.uncod.android.droidbooru.net;

import in.uncod.android.droidbooru.BooruFile;

public interface FilesDownloadedCallback {
    void onFilesDownloaded(int offset, BooruFile[] files);
}
