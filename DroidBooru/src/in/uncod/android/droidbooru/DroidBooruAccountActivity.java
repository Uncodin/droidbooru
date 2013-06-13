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
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.MenuItem.OnMenuItemClickListener;

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
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(R.string.switch_account).setOnMenuItemClickListener(new OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                // Let the user choose the new account
                Authenticator.launchAccountPicker(DroidBooruAccountActivity.this, REQ_CODE_CHOOSE_ACCOUNT,
                        mAccount, Authenticator.ACCOUNT_TYPE_DROIDBOORU);

                return true;
            }
        }).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (mAccount == null) {
            getDroidBooruAccount(false);
        }
    }

    protected abstract void onAccountLoaded(boolean switchingAccounts);

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_CODE_CHOOSE_ACCOUNT) {
            // Returned from choosing an account
            if (data != null) {
                String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);

                storeSelectedAccountName(accountName);

                getDroidBooruAccount(false);
                accountLoaded(mAccount != null);
            }
            else if (mAccount == null) {
                // No account selected
                Toast.makeText(this, R.string.must_select_account, Toast.LENGTH_LONG).show();
                finish();
            }
        }
        else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void getDroidBooruAccount(boolean switchingAccounts) {
        Account[] validAccounts = AccountManager.get(this).getAccountsByType(
                Authenticator.ACCOUNT_TYPE_DROIDBOORU);

        if (validAccounts.length > 0) {
            // Valid DroidBooru accounts exist
            String selectedAccount = getSelectedAccountName();
            if (selectedAccount != "") {
                // Use the previously selected account if possible
                for (Account acct : validAccounts) {
                    if (acct.name.equals(selectedAccount)) {
                        mAccount = acct;
                        accountLoaded(switchingAccounts);
                        break;
                    }
                }

                if (mAccount == null && !switchingAccounts) {
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
            Authenticator.launchAccountCreator(this, AccountManager.get(this), null);
        }
    }

    private void accountLoaded(boolean switchingAccounts) {
        if (mAccount != null) {
            // Start notification service
            Intent service = new Intent(this, NotificationService.class);
            startService(service);

            onAccountLoaded(switchingAccounts);
        }
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
