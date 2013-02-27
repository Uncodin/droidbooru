package in.uncod.android.droidbooru.auth;

import in.uncod.android.droidbooru.R;
import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import com.google.android.gms.common.AccountPicker;

/**
 * Account authenticator for DroidBooru accounts
 */
public class Authenticator extends AbstractAccountAuthenticator {
    /**
     * Account type handled by this authenticator
     */
    public static final String ACCOUNT_TYPE_DROIDBOORU = "in.uncod.android.droidbooru";

    /**
     * Key for accessing server name in user data
     */
    public static final String ACCOUNT_KEY_SERVER_NAME = "server_name";

    /**
     * Key for accessing server address in user data
     */
    public static final String ACCOUNT_KEY_SERVER_ADDRESS = "server_address";

    private Context mContext;

    public Authenticator(Context context) {
        super(context);

        mContext = context;
    }

    @Override
    public Bundle addAccount(AccountAuthenticatorResponse response, String accountType, String authTokenType,
            String[] requiredFeatures, Bundle options) throws NetworkErrorException {
        if (!accountType.equals(ACCOUNT_TYPE_DROIDBOORU))
            return null;

        // Return an intent to launch the account creation activity
        Intent addIntent = new Intent(mContext, CreateAccountActivity.class);
        addIntent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);

        Bundle addBundle = new Bundle();
        addBundle.putParcelable(AccountManager.KEY_INTENT, addIntent);

        return addBundle;
    }

    @Override
    public Bundle confirmCredentials(AccountAuthenticatorResponse response, Account account, Bundle options)
            throws NetworkErrorException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Bundle editProperties(AccountAuthenticatorResponse response, String accountType) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Bundle getAuthToken(AccountAuthenticatorResponse response, Account account, String authTokenType,
            Bundle options) throws NetworkErrorException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getAuthTokenLabel(String authTokenType) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Bundle hasFeatures(AccountAuthenticatorResponse response, Account account, String[] features)
            throws NetworkErrorException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Bundle updateCredentials(AccountAuthenticatorResponse response, Account account,
            String authTokenType, Bundle options) throws NetworkErrorException {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * Displays an account picker to the user
     * 
     * @param ctx
     *            Parent activity for the picker. This activity will have its onActivityResult called with the resulting
     *            account.
     * @param requestCode
     *            This request code will be passed to onActivityResult.
     * @param selectedAccount
     *            If not null, this Account will be rendered as currently selected in the picker.
     * @param types
     *            One or more account types. Only these types will be displayed in the picker.
     */
    public static void launchAccountPicker(Activity ctx, int requestCode, Account selectedAccount,
            String... types) {
        Intent intent = AccountPicker.newChooseAccountIntent(selectedAccount, null, types, true, null, null,
                null, null);

        if (ctx.getPackageManager().resolveActivity(intent, 0) == null) {
            // User probably needs the Google Play Services library
            Toast.makeText(ctx, R.string.google_play_services_error, Toast.LENGTH_LONG).show();

            intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.gms"));

            ctx.startActivity(intent);
        }
        else {
            ctx.startActivityForResult(intent, requestCode);
        }
    }

    /**
     * Launches an Activity to create a new account
     * 
     * @param ctx
     *            The parent Activity for the request
     * @param mgr
     *            AccountManager to use for the request
     */
    public static void launchAccountCreator(Activity ctx, AccountManager mgr) {
        mgr.addAccount(Authenticator.ACCOUNT_TYPE_DROIDBOORU, null, null, null, ctx,
                new CreateAccountCallback(mgr), null);
    }
}
