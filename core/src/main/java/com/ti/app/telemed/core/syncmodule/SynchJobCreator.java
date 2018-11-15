package com.ti.app.telemed.core.syncmodule;

import android.support.annotation.NonNull;
import com.evernote.android.job.Job;
import com.evernote.android.job.JobCreator;


public class SynchJobCreator implements JobCreator {
    @Override
    public Job create(@NonNull String tag) {
        switch (tag) {
            case SyncJob.JOB_TAG:
                return new SyncJob();
            default:
                return null;
        }
    }
}
