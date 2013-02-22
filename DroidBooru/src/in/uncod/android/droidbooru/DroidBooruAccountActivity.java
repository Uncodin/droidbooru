package in.uncod.android.droidbooru;

import in.uncod.android.droidbooru.net.NotificationService;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockActivity;
import com.google.android.gms.common.AccountPicker;

public abstract class DroidBooruAccountActivity extends SherlockActivity {
    private static final int REQ_CODE_CHOOSE_ACCOUNT = 8675309;

    protected Account mAccount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Workaround for AsyncTask troubles on Google TV
        // http://stackoverflow.com/a/7818839/1200865
        try {
            Class.forName("android.os.AsyncTask");
        }
        catch (ClassNotFoundException e1) {
            e1.printStackTrace();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        init();
    }

    private void init() {
        mAccount = getDroidBooruAccount();

        if (mAccount != null) {
            // Start notification service
            Intent service = new Intent(this, NotificationService.class);
            startService(service);

            onAccountLoaded();
        } // Else, the user is forwarded to an account chooser activity
    }

    protected abstract void onAccountLoaded();

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_CODE_CHOOSE_ACCOUNT) {
            // Returned from choosing an account
            if (data != null) {
                storeAccount(data);

                init();
            }
            else {
                // No account selected
                Toast.makeText(this, R.string.must_select_account, Toast.LENGTH_LONG).show();
                finish();
            }
        }
        else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private Account getDroidBooruAccount() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Resources resources = getResources();
        Account retAccount = null;

        // Get stored account if we have one
        String storedAccountName = prefs.getString(resources.getString(R.string.pref_account_name), "");
        if (storedAccountName != "") {
            retAccount = new Account(storedAccountName, prefs.getString(
                    resources.getString(R.string.pref_account_type), ""));
        }
        else {
            // Ask user to select account
            launchAccountPicker();
        }

        return retAccount;
    }

    protected void launchAccountPicker() {
        Intent intent = AccountPicker.newChooseAccountIntent(null, null, new String[] { "com.google" },
                false, null, null, null, null);

        try {
            startActivityForResult(intent, REQ_CODE_CHOOSE_ACCOUNT);
        }
        catch (ActivityNotFoundException e) {
            // User probably needs the Google Play Services library
            intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=com.google.android.gms"));

            startActivity(intent);
        }
    }

    private void storeAccount(Intent data) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Resources resources = this.getResources();

        String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
        String accountType = data.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE);

        storeAccountInPrefs(new Account(accountName, accountType), prefs, resources);
    }

    private void storeAccountInPrefs(Account account, SharedPreferences prefs, Resources resources) {
        Editor editor = prefs.edit();

        editor.putString(resources.getString(R.string.pref_account_name), account.name);
        editor.putString(resources.getString(R.string.pref_account_type), account.type);

        editor.commit();
    }
}
