package in.uncod.android.droidbooru;

import in.uncod.android.droidbooru.auth.Authenticator;
import in.uncod.android.droidbooru.net.NotificationService;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockFragmentActivity;

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
                String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);

                storeSelectedAccountName(accountName);

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
                    Authenticator.launchAccountPicker(this, REQ_CODE_CHOOSE_ACCOUNT, null,
                            Authenticator.ACCOUNT_TYPE_DROIDBOORU);
                }
            }
            else {
                // No previously selected account; let user choose
                Authenticator.launchAccountPicker(this, REQ_CODE_CHOOSE_ACCOUNT, null,
                        Authenticator.ACCOUNT_TYPE_DROIDBOORU);
            }
        }
        else {
            // No existing DroidBooru accounts; let user create one
            Authenticator.launchAccountCreator(this, AccountManager.get(this));
        }

        return retAccount;
    }

    private String getSelectedAccountName() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Resources resources = getResources();

        return prefs.getString(resources.getString(R.string.pref_selected_account_name), "");
    }

    private void storeSelectedAccountName(String accountName) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Resources resources = getResources();

        Editor editor = prefs.edit();

        editor.putString(resources.getString(R.string.pref_selected_account_name), accountName);

        editor.commit();
    }
}
