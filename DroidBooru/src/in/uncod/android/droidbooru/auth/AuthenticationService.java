package in.uncod.android.droidbooru.auth;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * Authentication service for DroidBooru accounts
 */
public class AuthenticationService extends Service {
    @Override
    public IBinder onBind(Intent intent) {
        return new Authenticator(this).getIBinder();
    }
}
