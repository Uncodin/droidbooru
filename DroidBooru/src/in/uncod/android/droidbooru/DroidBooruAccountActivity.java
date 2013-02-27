package in.uncod.android.droidbooru;

import in.uncod.android.droidbooru.auth.Authenticator;
import in.uncod.android.droidbooru.net.NotificationService;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.google.android.gms.common.AccountPicker;

public abstract class DroidBooruAccountActivity extends SherlockFragmentActivity {
    private static final int REQ_CODE_CHOOSE_ACCOUNT = 5309;

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
        Account retAccount = null;

        Account[] validAccounts = AccountManager.get(this).getAccountsByType(
                Authenticator.ACCOUNT_TYPE_DROIDBOORU);

        if (validAccounts.length > 0) {
            // Valid DroidBooru accounts exist
            String selectedAccount = getSelectedAccountName();
            if (selectedAccount != "") {
                // Use the previously selected account if possible
                for (Account acct : validAccounts) {
                    if (acct.name.equals(selectedAccount)) {
                        retAccount = acct;
                        break;
                    }
                }

                if (retAccount == null) {
                    // Selected account doesn't exist; let user choose
                    launchAccountPicker(Authenticator.ACCOUNT_TYPE_DROIDBOORU);
                }
            }
            else {
                // No previously selected account; let user choose
                launchAccountPicker(Authenticator.ACCOUNT_TYPE_DROIDBOORU);
            }
        }
        else {
            // No existing DroidBooru accounts
            launchAccountPicker("com.google");
        }

        return retAccount;
    }

    protected void launchAccountPicker(String... types) {
        Intent intent = AccountPicker.newChooseAccountIntent(null, null, types, true, null, null, null, null);

        if (getPackageManager().resolveActivity(intent, 0) == null) {
            // User probably needs the Google Play Services library
            Toast.makeText(this, R.string.google_play_services_error, Toast.LENGTH_LONG).show();

            intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.gms"));

            startActivity(intent);
        }
        else {
            startActivityForResult(intent, REQ_CODE_CHOOSE_ACCOUNT);
        }
    }

    private String getSelectedAccountName() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Resources resources = getResources();

        return prefs.getString(resources.getString(R.string.pref_selected_account_name), "");
    }

    private void storeAccount(Intent data) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Resources resources = this.getResources();

        String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
        String accountType = data.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE);

        if (accountType.equals("com.google")) {
            // Convert to a DroidBooru account
            accountType = Authenticator.ACCOUNT_TYPE_DROIDBOORU;
            Bundle accountData = new Bundle();

            // TODO Store server information entered by user
            accountData.putString(Authenticator.ACCOUNT_KEY_SERVER_NAME, "Uncodin");
            accountData.putString(Authenticator.ACCOUNT_KEY_SERVER_ADDRESS, "img.uncod.in");

            AccountManager.get(this).addAccountExplicitly(new Account(accountName, accountType), null,
                    accountData);
        }

        storeAccountInPrefs(new Account(accountName, accountType), prefs, resources);
    }

    private void storeAccountInPrefs(Account account, SharedPreferences prefs, Resources resources) {
        Editor editor = prefs.edit();

        editor.putString(resources.getString(R.string.pref_selected_account_name), account.name);

        editor.commit();
    }
}
