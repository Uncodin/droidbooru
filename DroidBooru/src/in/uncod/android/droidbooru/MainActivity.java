package in.uncod.android.droidbooru;

import in.uncod.android.droidbooru.net.FilesUploadedCallback;
import in.uncod.android.net.ConnectivityAgent;

import java.io.File;
import java.util.ArrayList;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MenuItem;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem.OnMenuItemClickListener;

public class MainActivity extends SherlockActivity {
    private static final String TAG = "MainActivity";

    /**
     * Request code to get a single file
     */
    private static final int REQ_CODE_GET_FILE = 0;

    private Account mAccount;
    private SharedPreferences mPrefs;

    private Handler mUiHandler;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Login settings
        menu.add(R.string.settings).setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_NEVER)
                .setOnMenuItemClickListener(new OnMenuItemClickListener() {
                    public boolean onMenuItemClick(com.actionbarsherlock.view.MenuItem item) {
                        startActivity(new Intent(MainActivity.this, LoginSettingsActivity.class));

                        return true;
                    }
                });

        return super.onCreateOptionsMenu(menu);
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mUiHandler = new Handler();

        Resources resources = getResources();

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        getAndStoreAccount(resources);

        // Initialize backend
        Backend.init(
                getExternalFilesDir(null),
                getExternalCacheDir(),
                mPrefs.getString(resources.getString(R.string.pref_selected_server),
                        resources.getString(R.string.dv_pref_selected_server)), new ConnectivityAgent(this));

        Intent intent = getIntent();
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            // Started via Share request
            Log.d(TAG, "Received single file upload request");

            // Uploading a single file
            Backend.getInstance().uploadFiles(
                    new File[] { Backend.getInstance().createTempFileForUri(
                            (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM), getContentResolver()) },
                    mAccount.name, Backend.getInstance().getDefaultTags(), mUiHandler,
                    new FilesUploadedCallback() {
                        public void onFilesUploaded(boolean error) {
                            if (error) {
                                setResult(RESULT_CANCELED);
                            }
                            else {
                                setResult(RESULT_OK);
                            }

                            finish();
                        }
                    }, GalleryActivity.createUploadingProgressDialog(MainActivity.this));
        }
        else if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
            // Started via Share request
            Log.d(TAG, "Received multiple file upload request");

            // Uploading multiple files
            ArrayList<Uri> fileUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            File[] files = new File[fileUris.size()];

            int i = 0;
            for (Uri uri : fileUris) {
                files[i] = Backend.getInstance().createTempFileForUri(uri, getContentResolver());

                i++;
            }

            Backend.getInstance().uploadFiles(files, mAccount.name, Backend.getInstance().getDefaultTags(),
                    mUiHandler, new FilesUploadedCallback() {
                        public void onFilesUploaded(boolean error) {
                            if (error) {
                                setResult(RESULT_CANCELED);
                            }
                            else {
                                setResult(RESULT_OK);
                            }

                            finish();
                        }
                    }, GalleryActivity.createUploadingProgressDialog(MainActivity.this));
        }
        else {
            // Need to launch the gallery
            Intent galleryIntent = new Intent(this, GalleryActivity.class).putExtra(
                    resources.getString(R.string.pref_account_name), mAccount).setAction(intent.getAction());

            if (intent.getAction().equals(Intent.ACTION_GET_CONTENT)) {
                startActivityForResult(galleryIntent, REQ_CODE_GET_FILE);
            }
            else {
                startActivity(galleryIntent);
                finish();
            }
        }
    }

    private void getAndStoreAccount(Resources resources) {
        // Get stored account if we have one
        String storedAccountName = mPrefs.getString(resources.getString(R.string.pref_account_name), "");
        if (storedAccountName != "") {
            mAccount = new Account(storedAccountName, mPrefs.getString(
                    resources.getString(R.string.pref_account_type), ""));
        }
        else {
            Account[] accounts = AccountManager.get(this).getAccounts();
            if (accounts.length > 0) {
                // TODO Let user pick account
                for (Account account : accounts) {
                    if (account.name.endsWith("ironclad.mobi")) {
                        mAccount = account;
                    }
                }

                if (mAccount == null)
                    mAccount = createDefaultAccount();

                storeAccountInPrefs(mAccount, resources);
            }
            else {
                // Proceed with a default account
                mAccount = createDefaultAccount();
            }
        }

        Log.d(TAG, "Using account " + mAccount.name);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_CODE_GET_FILE) {
            // Received data back from gallery; exit
            setResult(resultCode, data);
            finish();
        }
        else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void storeAccountInPrefs(Account account, Resources resources) {
        Editor editor = mPrefs.edit();

        editor.putString(resources.getString(R.string.pref_account_name), account.name);
        editor.putString(resources.getString(R.string.pref_account_type), account.type);

        editor.commit();
    }

    private Account createDefaultAccount() {
        return new Account("droidbooru@ironclad.mobi", "Google");
    }
}
