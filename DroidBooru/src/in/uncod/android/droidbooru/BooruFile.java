package in.uncod.android.droidbooru;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

import android.util.Log;
import android.webkit.MimeTypeMap;

public class BooruFile {
    private static final String TAG = "BooruFile";

    private String mDownloadPathPrefix;
    private URL mThumbUrl;
    private URL mActualUrl;
    private File mThumbFile;
    private String mMimeType;
    private String mFilehash;
    private String mUniqueId;

    private BooruFile(String downloadPathPrefix, String mimeType, String fileHash, String uniqueId,
            URL thumbUrl, URL actualUrl) {
        mDownloadPathPrefix = downloadPathPrefix;
        mThumbUrl = thumbUrl;
        mActualUrl = actualUrl;
        mMimeType = mimeType;
        mFilehash = fileHash;
        mUniqueId = uniqueId;
    }

    public static BooruFile create(String downloadPathPrefix, String mimeType, String fileHash,
            String uniqueId, URL baseThumbUrl, URL baseFileUrl) {
        return new BooruFile(downloadPathPrefix, mimeType, fileHash, uniqueId, getThumbUrlForFile(mimeType,
                fileHash, baseThumbUrl), getActualUrlForFile(mimeType, fileHash, baseFileUrl));
    }

    private static URL getThumbUrlForFile(String mimeType, String fileHash, URL baseThumbUrl) {
        String extension = getFileExtension(mimeType);

        URL fileThumbUrl = null;

        try {
            if (mimeType.contains("audio") || mimeType.contains("video")) {
                String thumbUrl;
                if (mimeType.contains("audio")) {
                    thumbUrl = baseThumbUrl + "music.png";
                }
                else {
                    thumbUrl = baseThumbUrl + "video.png";
                }

                fileThumbUrl = new URL(thumbUrl);
            }
            else {
                String thumbUrl;

                if (extension.equals("undefined")) {
                    thumbUrl = baseThumbUrl + "temp_thumb.jpg";
                }
                else if (extension.equals("gif")) {
                    thumbUrl = baseThumbUrl + fileHash + "_thumb-0.jpg";
                }
                else {
                    thumbUrl = baseThumbUrl + fileHash + "_thumb.jpg";
                }

                fileThumbUrl = new URL(thumbUrl);
            }
        }
        catch (MalformedURLException e) {
            Log.e(TAG, "Couldn't parse URL for file " + fileHash + "." + extension);
        }

        return fileThumbUrl;
    }

    private static URL getActualUrlForFile(String mimeType, String fileHash, URL baseFileUrl) {
        String extension = getFileExtension(mimeType);

        URL url = null;

        try {
            url = new URL(baseFileUrl + fileHash + "." + extension);
        }
        catch (MalformedURLException e) {
            Log.e(TAG, "Couldn't parse URL for file " + fileHash + "." + extension);
        }

        return url;
    }

    public static String getFileExtension(String mimeType) {
        String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        if (extension == null) {
            extension = "undefined";
        }

        return extension;
    }

    public URL getThumbUrl() {
        return mThumbUrl;
    }

    public URL getActualUrl() {
        return mActualUrl;
    }

    public String getMime() {
        return mMimeType;
    }

    public File getThumbPath() {
        File filePath;

        if (getThumbFile() != null && getThumbFile().exists()) {
            filePath = getThumbFile();
        }
        else {
            // File doesn't exist, try the default thumbnail image
            filePath = new File(mDownloadPathPrefix, "temp_thumb.jpg");

            if (!filePath.exists()) {
                // No luck; return null
                filePath = null;
            }
        }

        return filePath;
    }

    public void setThumbFile(File file) {
        mThumbFile = file;
    }

    public File getThumbFile() {
        return mThumbFile;
    }

    public String getFilehash() {
        return mFilehash;
    }

    public String getMimeForLaunch() {
        String mimeType = getMime();
        String extension = getFileExtension(mMimeType);

        if (extension == "gif" || extension == "undefined") {
            mimeType = "text/html"; // Allow the file to be opened in a browser and viewed/downloaded
        }

        return mimeType;
    }

    public String getUniqueId() {
        return mUniqueId;
    }
}
