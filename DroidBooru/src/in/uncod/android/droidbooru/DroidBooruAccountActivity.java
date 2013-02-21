package in.uncod.android.droidbooru;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.net.Uri;
import android.preference.PreferenceManager;

import com.actionbarsherlock.app.SherlockActivity;
import com.google.android.gms.common.AccountPicker;

public class DroidBooruAccountActivity extends SherlockActivity {
    public static final int REQ_CODE_CHOOSE_ACCOUNT = 8675309;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_CODE_CHOOSE_ACCOUNT) {
            if (data != null) {
                storeAccount(data);
            }
            else {
                // No account selected
                finish();
            }
        }
        else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    public Account getDroidBooruAccount() {
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
            // Let user pick account
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
                finish();
            }
        }

        return retAccount;
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
