package in.uncod.android.droidbooru;

import in.uncod.android.droidbooru.backend.Backend;
import in.uncod.android.droidbooru.net.FilesUploadedCallback;
import in.uncod.android.droidbooru.net.NotificationService;
import in.uncod.android.util.threading.TaskWithResultListener.OnTaskResultListener;

import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpHost;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
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

        // Workaround for AsyncTask troubles on Google TV
        // http://stackoverflow.com/a/7818839/1200865
        try {
            Class.forName("android.os.AsyncTask");
        }
        catch (ClassNotFoundException e1) {
            e1.printStackTrace();
        }

        mUiHandler = new Handler();

        mAccount = getDroidBooruAccount(this);

        // Start notification service
        Intent service = new Intent(this, NotificationService.class);
        startService(service);

        Intent intent = getIntent();
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            // Started via Share request
            Log.d(TAG, "Received single file upload request");

            Uri shareUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (shareUri == null) {
                // No direct file URI; see if there's a URI in the text extra
                shareUri = Uri.parse(intent.getStringExtra(Intent.EXTRA_TEXT));
            }

            Log.d(TAG, "Shared URI is " + shareUri);

            if (shareUri.getScheme().equals(HttpHost.DEFAULT_SCHEME_NAME)) {
                // URI is HTTP link to file; download it first
                try {
                    Backend.getInstance().downloadTempFileFromHttp(shareUri,
                            new OnTaskResultListener<List<File>>() {
                                public void onTaskResult(List<File> result) {
                                    if (result.size() > 0) {
                                        uploadSingleFile(Uri.fromFile(result.get(0)));
                                    }
                                    else {
                                        setResult(RESULT_CANCELED);
                                        finish();
                                    }
                                }
                            }, GalleryActivity.createDownloadingProgressDialog(this));
                }
                catch (MalformedURLException e) {
                    e.printStackTrace();

                    setResult(RESULT_CANCELED);
                    finish();
                }
            }
            else {
                // Uploading a single file
                uploadSingleFile(shareUri);
            }
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

            uploadMultipleFiles(files);
        }
        else {
            // Need to launch the gallery
            Intent galleryIntent = new Intent(this, GalleryActivity.class).setAction(intent.getAction());

            if (Intent.ACTION_GET_CONTENT.equals(intent.getAction())) {
                startActivityForResult(galleryIntent, REQ_CODE_GET_FILE);
            }
            else {
                startActivity(galleryIntent);
                finish();
            }
        }
    }

    private void uploadMultipleFiles(File[] files) {
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

    private void uploadSingleFile(Uri fileUri) {
        Backend.getInstance().uploadFiles(
                new File[] { Backend.getInstance().createTempFileForUri(fileUri, getContentResolver()) },
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

    public static Account getDroidBooruAccount(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Resources resources = context.getResources();
        Account retAccount = null;

        // Get stored account if we have one
        String storedAccountName = prefs.getString(resources.getString(R.string.pref_account_name), "");
        if (storedAccountName != "") {
            retAccount = new Account(storedAccountName, prefs.getString(
                    resources.getString(R.string.pref_account_type), ""));
        }
        else {
            Account[] accounts = AccountManager.get(context).getAccounts();
            if (accounts.length > 0) {
                // TODO Let user pick account
                for (Account account : accounts) {
                    if (account.name.endsWith("ironclad.mobi")) {
                        retAccount = account;
                        break;
                    }
                }

                if (retAccount == null)
                    retAccount = createDefaultAccount();

                storeAccountInPrefs(retAccount, prefs, resources);
            }
            else {
                // Proceed with a default account
                retAccount = createDefaultAccount();
            }
        }

        return retAccount;
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

    private static void storeAccountInPrefs(Account account, SharedPreferences prefs, Resources resources) {
        Editor editor = prefs.edit();

        editor.putString(resources.getString(R.string.pref_account_name), account.name);
        editor.putString(resources.getString(R.string.pref_account_type), account.type);

        editor.commit();
    }

    private static Account createDefaultAccount() {
        return new Account("droidbooru@ironclad.mobi", "Google");
    }
}
