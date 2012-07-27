package in.uncod.android.util.threading;

import android.os.AsyncTask;

public abstract class TaskWithResultListener<TParams, TProgress, TResult> extends
        AsyncTask<TParams, TProgress, TResult> {
    public static interface OnResultListener<TResult> {
        public void onTaskResult(TResult files);
    }

    public TaskWithResultListener(OnResultListener<TResult> listener) {
        mListener = listener;
    }

    private OnResultListener<TResult> mListener;

    protected void onPostExecute(TResult result) {
        if (mListener != null) {
            mListener.onTaskResult(result);
        }
    };
}