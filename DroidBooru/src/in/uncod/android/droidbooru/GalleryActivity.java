package in.uncod.android.droidbooru;

import in.uncod.android.droidbooru.fragments.GalleryFragment;
import in.uncod.android.droidbooru.fragments.GalleryFragment.IGalleryContainer;
import android.accounts.Account;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.actionbarsherlock.view.Window;

public class GalleryActivity extends DroidBooruAccountActivity implements IGalleryContainer {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS); // So we can show progress while downloading
        setContentView(R.layout.activity_gallery);
    }

    protected void onAccountLoaded(boolean switchingAccounts) {
        GalleryFragment frag = (GalleryFragment) getSupportFragmentManager().findFragmentById(
                R.id.fragment_gallery);

        frag.load(switchingAccounts);

        String action = getIntent().getAction();
        if (action != null && action.equals(Intent.ACTION_GET_CONTENT)) {
            // User is selecting content for another app
            frag.beginSelection();
        }
    }

    @Override
    public Account getAccount() {
        return mAccount;
    }

    @Override
    public void onSelectionEnded() {
        String action = getIntent().getAction();
        if (action != null && action.equals(Intent.ACTION_GET_CONTENT)) {
            // User was picking content for another app
            setResult(Activity.RESULT_CANCELED);
            finish();
        }
    }

    @Override
    public void onSelectedFiles(Intent data) {
        setResult(Activity.RESULT_OK, data);
        finish();
    }
}
