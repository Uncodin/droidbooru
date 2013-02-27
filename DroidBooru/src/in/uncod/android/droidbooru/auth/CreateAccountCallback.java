package in.uncod.android.droidbooru.auth;

import java.io.IOException;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.os.Bundle;

/**
 * This callback receives account data input by a user and creates an appropriate account
 */
public class CreateAccountCallback implements AccountManagerCallback<Bundle> {
    private final AccountManager mgr;

    public CreateAccountCallback(AccountManager mgr) {
        this.mgr = mgr;
    }

    @Override
    public void run(AccountManagerFuture<Bundle> future) {
        try {
            Bundle result = future.getResult();

            String accountName = result.getString(AccountManager.KEY_ACCOUNT_NAME);
            String serverName = result.getString(Authenticator.ACCOUNT_KEY_SERVER_NAME);
            String serverAddress = result.getString(Authenticator.ACCOUNT_KEY_SERVER_ADDRESS);

            createAccount(mgr, accountName, serverName, serverAddress);
        }
        catch (AuthenticatorException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (OperationCanceledException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void createAccount(final AccountManager mgr, String accountName, String serverName,
            String serverAddress) {
        Bundle data = new Bundle();
        data.putString(Authenticator.ACCOUNT_KEY_SERVER_NAME, serverName);
        data.putString(Authenticator.ACCOUNT_KEY_SERVER_ADDRESS, serverAddress);

        mgr.addAccountExplicitly(new Account(accountName, Authenticator.ACCOUNT_TYPE_DROIDBOORU), null, data);
    }
}