package in.uncod.android.util.threading;

import android.app.ProgressDialog;

public abstract class TaskWithProgressAndListener<TParams, TProgress, TResult> extends
        TaskWithResultListener<TParams, TProgress, TResult> {

    protected ProgressDialog mDialog;

    public TaskWithProgressAndListener(OnResultListener<TResult> listener, ProgressDialog dialog) {
        super(listener);

        mDialog = dialog;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();

        if (mDialog != null) {
            mDialog.setProgress(0);
            mDialog.show();
        }
    }

    @Override
    protected final void onPostExecute(final TResult result) {
        super.onPostExecute(result);

        if (mDialog != null) {
            mDialog.dismiss();
        }
    }
}