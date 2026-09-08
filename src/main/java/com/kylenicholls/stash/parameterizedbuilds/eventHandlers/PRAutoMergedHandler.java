package com.kylenicholls.stash.parameterizedbuilds.eventHandlers;

import com.atlassian.bitbucket.branch.cascadingmerge.CascadingMergeEvent;
import com.atlassian.bitbucket.pull.PullRequestService;
import com.atlassian.bitbucket.repository.Branch;
import com.kylenicholls.stash.parameterizedbuilds.ciserver.Jenkins;
import com.kylenicholls.stash.parameterizedbuilds.helper.SettingsService;
import com.kylenicholls.stash.parameterizedbuilds.item.BitbucketVariables;
import com.kylenicholls.stash.parameterizedbuilds.item.Job.Trigger;

public class PRAutoMergedHandler extends PRHandler {

    private Branch branch;

    public PRAutoMergedHandler(SettingsService settingsService,
                               Jenkins jenkins,
                               CascadingMergeEvent event,
                               String url,
                               Branch branch) {
        super(settingsService, jenkins, event, url, Trigger.PRAUTOMERGED);
        this.branch = branch;
    }

    @Override
    BitbucketVariables createBitbucketVariables() {
        return new BitbucketVariables.Builder()
                .populateFromBranch(branch, repository, projectKey, trigger, url)
                .build();
    }
}