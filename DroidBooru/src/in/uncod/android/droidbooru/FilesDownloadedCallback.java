package in.uncod.android.droidbooru;

public interface FilesDownloadedCallback {
    void onFilesDownloaded(int offset, BooruFile[] files);
}
