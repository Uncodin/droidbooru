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
import android.widget.Toast;

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

    @Override
    public void onCreate() {
        if (mTimer == null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            Resources resources = getResources();

            // Initialize backend
            Backend.init(getExternalFilesDir(null), getExternalCacheDir(), prefs.getString(
                    resources.getString(R.string.pref_selected_server),
                    resources.getString(R.string.dv_pref_selected_server)), new ConnectivityAgent(this));

            // Schedule check for new images every N minutes
            mTimer = new Timer("ImageUpdate", true);
            mTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Log.d(TAG, "Checking for new images...");

                    Backend.getInstance().queryExternalFiles(1, 0, new AbstractNetworkCallbacks() {
                        @Override
                        public void onReceivedImage(ORMDatastore ds, String queryName, Image[] d) {
                            if (d.length > 0) {
                                if (mNewestImage == null) {
                                    // First run; save newest image
                                    mNewestImage = d[0].getPid();
                                }
                                else {
                                    if (!mNewestImage.equals(d[0].getPid())) {
                                        // Save newest image and send notification
                                        mNewestImage = d[0].getPid();

                                        showNotification();
                                    }
                                }
                            }
                        }
                    });
                }
            }, Calendar.getInstance().getTime(), IMAGE_CHECK_INTERVAL_MILLIS);

            Toast.makeText(getApplicationContext(), R.string.notification_service_started, Toast.LENGTH_SHORT)
                    .show();
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

        Notification newImageNotification = new Notification(R.drawable.ic_launcher, getResources()
                .getString(R.string.new_image_notification), IMAGE_CHECK_INTERVAL_MILLIS);

        newImageNotification.defaults |= Notification.DEFAULT_ALL;
        newImageNotification.flags |= Notification.FLAG_AUTO_CANCEL;
        Intent galleryIntent = new Intent(this, GalleryActivity.class);
        PendingIntent i = PendingIntent.getActivity(getBaseContext(), 0, galleryIntent,
                Intent.FLAG_ACTIVITY_NEW_TASK);

        newImageNotification.setLatestEventInfo(getApplicationContext(),
                getResources().getString(R.string.new_image_notification), null, i);

        notifier.notify(ID_NEW_IMAGE_NOTIFICATION, newImageNotification);
    }
}
