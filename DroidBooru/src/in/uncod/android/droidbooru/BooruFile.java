package in.uncod.android.droidbooru;

import in.uncod.nativ.Image;
import in.uncod.nativ.ORMDatastore;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

import android.util.Log;
import android.webkit.MimeTypeMap;

public class BooruFile {
    private static final String TAG = "BooruFile";

    private ORMDatastore mDatastore;
    private Image mImage;
    private URL mThumbUrl;
    private URL mActualUrl;
    private File mThumbFile;

    private BooruFile(ORMDatastore datastore, Image file, URL thumbUrl, URL actualUrl) {
        mDatastore = datastore;
        mImage = file;
        mThumbUrl = thumbUrl;
        mActualUrl = actualUrl;
    }

    public static BooruFile create(ORMDatastore datastore, Image file, URL baseThumbUrl, URL baseFileUrl) {
        return new BooruFile(datastore, file, getThumbUrlForFile(file, baseThumbUrl), getActualUrlForFile(
                file, baseFileUrl));
    }

    private static URL getThumbUrlForFile(Image file, URL baseThumbUrl) {
        String mimeType = file.getMime();
        String extension = getFileExtension(file);

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
                    thumbUrl = baseThumbUrl + file.getFilehash() + "_thumb-0.jpg";
                }
                else {
                    thumbUrl = baseThumbUrl + file.getFilehash() + "_thumb.jpg";
                }

                fileThumbUrl = new URL(thumbUrl);
            }
        }
        catch (MalformedURLException e) {
            Log.e(TAG, "Couldn't parse URL for file " + file.getFilehash() + "." + extension);
        }

        return fileThumbUrl;
    }

    private static URL getActualUrlForFile(Image file, URL baseFileUrl) {
        String extension = getFileExtension(file);

        URL url = null;

        try {
            url = new URL(baseFileUrl + file.getFilehash() + "." + extension);
        }
        catch (MalformedURLException e) {
            Log.e(TAG, "Couldn't parse URL for file " + file.getFilehash() + "." + extension);
        }

        return url;
    }

    public static String getFileExtension(Image file) {
        String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(file.getMime());
        if (extension == null) {
            extension = "undefined";
        }

        return extension;
    }

    public Image getFile() {
        return mImage;
    }

    public URL getThumbUrl() {
        return mThumbUrl;
    }

    public URL getActualUrl() {
        return mActualUrl;
    }

    public String getMime() {
        return mImage.getMime();
    }

    public File getThumbPath() {
        File filePath;

        if (getThumbFile() != null && getThumbFile().exists()) {
            filePath = getThumbFile();
        }
        else {
            // File doesn't exist, try the default thumbnail image
            filePath = new File(mDatastore.getDownloadPathPrefix(), "temp_thumb.jpg");

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
        return mImage.getFilehash();
    }

    public String getMimeForLaunch() {
        String mimeType = getMime();
        String extension = getFileExtension(mImage);

        if (extension == "gif" || extension == "undefined") {
            mimeType = "text/html"; // Allow the file to be opened in a browser and viewed/downloaded
        }

        return mimeType;
    }
}
