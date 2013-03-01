package in.uncod.android.droidbooru.auth;

import in.uncod.android.droidbooru.R;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.TextView;

/**
 * This activity provides a user interface for creating accounts
 */
public class CreateAccountActivity extends AccountAuthenticatorActivity {
    private static final int REQ_CODE_SELECT_GOOGLE_ACCOUNT = 0;
    private Account mSelectedAccount;
    private TextView mPreview;
    private EditText mServerName;
    private EditText mServerAddress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_account);

        mPreview = (TextView) findViewById(R.id.preview_google_account);
        mServerName = (EditText) findViewById(R.id.input_server_name);
        mServerAddress = (EditText) findViewById(R.id.input_server_address);

        findViewById(R.id.select_google_account).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Authenticator.launchAccountPicker(CreateAccountActivity.this, REQ_CODE_SELECT_GOOGLE_ACCOUNT,
                        mSelectedAccount, "com.google");
            }
        });

        findViewById(R.id.save_account).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // Validate inputs
                if (mSelectedAccount == null || mServerName.length() == 0 || mServerAddress.length() == 0)
                    return;

                // Prepare to return result to AccountManager
                Bundle result = new Bundle();

                result.putString(Authenticator.ACCOUNT_KEY_SERVER_NAME, mServerName.getText().toString());
                result.putString(Authenticator.ACCOUNT_KEY_SERVER_ADDRESS, mServerAddress.getText()
                        .toString());

                // Create new account
                Bundle userdata = new Bundle(result);
                AccountManager.get(CreateAccountActivity.this).addAccountExplicitly(
                        new Account(mSelectedAccount.name, Authenticator.ACCOUNT_TYPE_DROIDBOORU), null,
                        userdata);

                result.putString(AccountManager.KEY_ACCOUNT_NAME, mSelectedAccount.name);
                result.putString(AccountManager.KEY_ACCOUNT_TYPE, Authenticator.ACCOUNT_TYPE_DROIDBOORU);

                setAccountAuthenticatorResult(result);
                finish();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_CODE_SELECT_GOOGLE_ACCOUNT) {
            if (data != null) {
                // Update selected account
                mSelectedAccount = new Account(data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME),
                        data.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE));

                mPreview.setText(mSelectedAccount.name);
                mPreview.setVisibility(View.VISIBLE);
            }
        }
        else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }
}
