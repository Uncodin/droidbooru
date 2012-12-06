package in.uncod.android.droidbooru.net;

import in.uncod.android.droidbooru.Backend;
import in.uncod.android.droidbooru.GalleryActivity;
import in.uncod.android.droidbooru.R;
import in.uncod.android.net.ConnectivityAgent;
import in.uncod.nativ.AbstractNetworkCallbacks;
import in.uncod.nativ.Image;
import in.uncod.nativ.ORMDatastore;
import in.uncod.nativ.ORMGuid;

import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

public class NotificationService extends Service {
    /**
     * The number of milliseconds between checks for new images
     */
    private static final long IMAGE_CHECK_INTERVAL_MILLIS = 1000 * 60 * 5; // Five minutes

    /**
     * ID for new image notifications
     */
    private static final int ID_NEW_IMAGE_NOTIFICATION = 0;

    protected static final String TAG = "NotificationService";

    private ORMGuid mNewestImage;
    private Timer mTimer;
    private String mServerName;

    @Override
    public void onCreate() {
        if (mTimer == null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            Resources resources = getResources();

            // Determine selected server address & name
            String[] serverNames = resources.getStringArray(R.array.server_list);
            String[] serverAddresses = resources.getStringArray(R.array.server_list_values);

            String selectedServerAddress = prefs.getString(
                    resources.getString(R.string.pref_selected_server),
                    resources.getString(R.string.dv_pref_selected_server));

            int i = 0;
            for (String name : serverNames) {
                if (serverAddresses[i].equals(selectedServerAddress)) {
                    mServerName = name;
                    break;
                }

                i++;
            }

            // Initialize backend
            Backend.init(getExternalFilesDir(null), getExternalCacheDir(), selectedServerAddress,
                    new ConnectivityAgent(this));

            // Schedule check for new files every N minutes
            mTimer = new Timer("ImageUpdate", true);
            mTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Log.d(TAG, "Checking for new images...");

                    Backend.getInstance().queryExternalFiles(1, 0, new AbstractNetworkCallbacks() {
                        @Override
                        public void onReceivedImage(ORMDatastore ds, String queryName, Image[] files) {
                            if (files.length > 0) {
                                if (mNewestImage == null) {
                                    // First run; save newest file
                                    mNewestImage = files[0].getPid();
                                }
                                else {
                                    if (!mNewestImage.equals(files[0].getPid())) {
                                        // Save newest file and send notification
                                        mNewestImage = files[0].getPid();

                                        showNotification();
                                    }
                                }
                            }
                        }
                    });
                }
            }, Calendar.getInstance().getTime(), IMAGE_CHECK_INTERVAL_MILLIS);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    // Using deprecated procedures to support 2.2
    @SuppressWarnings("deprecation")
    private void showNotification() {
        NotificationManager notifier = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // Get formatted text strings
        String statusBarText = getResources().getString(R.string.new_file_notification_status_bar,
                mServerName);
        String bodyText = getResources().getString(R.string.new_file_notification_body, mServerName);

        Notification newImageNotification = new Notification(R.drawable.ic_launcher, statusBarText,
                System.currentTimeMillis());

        newImageNotification.defaults |= Notification.DEFAULT_ALL;
        newImageNotification.flags |= Notification.FLAG_AUTO_CANCEL;

        // Set up intent to launch gallery view
        Intent galleryIntent = new Intent(this, GalleryActivity.class);
        PendingIntent i = PendingIntent.getActivity(getBaseContext(), 0, galleryIntent,
                Intent.FLAG_ACTIVITY_NEW_TASK);

        newImageNotification.setLatestEventInfo(getApplicationContext(),
                getResources().getString(R.string.new_file_notification_title), bodyText, i);

        // Send notification
        notifier.notify(ID_NEW_IMAGE_NOTIFICATION, newImageNotification);
    }
}
