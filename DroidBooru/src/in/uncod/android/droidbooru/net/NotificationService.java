package in.uncod.android.droidbooru.net;

import in.uncod.android.droidbooru.auth.Authenticator;

import java.util.ArrayList;
import java.util.List;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class NotificationService extends Service {
    protected static final String TAG = "NotificationService";
    private boolean mInitialized;
    private List<Notifier> mNotifiers = new ArrayList<Notifier>();

    @Override
    public void onCreate() {
        if (!mInitialized) {
            mInitialized = true;

            AccountManager mgr = AccountManager.get(this);
            Account[] validAccounts = mgr
                    .getAccountsByType(Authenticator.ACCOUNT_TYPE_DROIDBOORU);

            // Create a Notifier for each DroidBooru account
            for (final Account acct : validAccounts) {
                mNotifiers.add(new Notifier(this, mgr.getUserData(acct,
                        Authenticator.ACCOUNT_KEY_SERVER_NAME), acct));
            }
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
}
