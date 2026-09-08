package com.kylenicholls.stash.parameterizedbuilds.eventHandlers;

import com.atlassian.bitbucket.content.AbstractChangeCallback;
import com.atlassian.bitbucket.content.Change;
import com.atlassian.bitbucket.content.ChangeContext;
import com.atlassian.bitbucket.content.ChangeSummary;
import com.atlassian.bitbucket.branch.cascadingmerge.CascadingMergeEvent;
import com.atlassian.bitbucket.event.pull.PullRequestEvent;
import com.atlassian.bitbucket.hook.repository.RepositoryHook;
import com.atlassian.bitbucket.hook.repository.RepositoryHookDetails;
import com.atlassian.bitbucket.pull.PullRequest;
import com.atlassian.bitbucket.pull.PullRequestChangesRequest;
import com.atlassian.bitbucket.pull.PullRequestService;
import com.atlassian.bitbucket.setting.Settings;
import com.kylenicholls.stash.parameterizedbuilds.PullRequestHook;
import com.kylenicholls.stash.parameterizedbuilds.ciserver.Jenkins;
import com.kylenicholls.stash.parameterizedbuilds.helper.SettingsService;
import com.kylenicholls.stash.parameterizedbuilds.item.BitbucketVariables;
import com.kylenicholls.stash.parameterizedbuilds.item.Job;
import com.kylenicholls.stash.parameterizedbuilds.item.Job.Trigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class PRHandler extends BaseHandler {

    private PullRequestService pullRequestService;
    PullRequest pullRequest;
    String url;
    final Trigger trigger;
    private static final Logger logger = LoggerFactory.getLogger(PRHandler.class);

    /**
     * Constructor for normal pull-request events:
     * opened, reopened, rescoped, merged, declined, deleted, approved, etc.
     */
    public PRHandler(
            SettingsService settingsService,
            PullRequestService pullRequestService,
            Jenkins jenkins,
            PullRequestEvent event,
            String url,
            Trigger trigger) {
        super(settingsService, jenkins);

        this.pullRequestService = pullRequestService;
        this.pullRequest = event.getPullRequest();
        this.user = pullRequest.getAuthor().getUser();
        this.repository = pullRequest.getToRef().getRepository();
        this.projectKey = repository.getProject().getKey();
        this.url = url;
        this.trigger = trigger;
    }

    /**
     * Constructor for cascading-merge events.
     */
    public PRHandler(
            SettingsService settingsService,
            Jenkins jenkins,
            CascadingMergeEvent event,
            String url,
            Trigger trigger) {
        super(settingsService, jenkins);

        this.repository = event.getRepository();
        this.projectKey = repository.getProject().getKey();
        this.url = url;
        this.trigger = trigger;
    }

    @Override
    public void run() {
        logger.debug(
                "PRHandler.run entered: handler={}, trigger={}, repository={}/{}, projectKey={}",
                getClass().getSimpleName(),
                trigger,
                repository.getProject().getKey(),
                repository.getSlug(),
                projectKey);

        RepositoryHook hook = settingsService.getHook(repository);

        if (hook == null) {
            logger.warn(
                    "No repository hook found for {}/{}",
                    repository.getProject().getKey(),
                    repository.getSlug());
            return;
        }

        RepositoryHookDetails details = hook.getDetails();

        logger.debug(
                "Repository hook returned: class={}, key={}, enabled={}, configured={}",
                hook.getClass().getName(),
                details == null ? null : details.getKey(),  /* is null during testing */
                hook.isEnabled(),
                hook.isConfigured());

        if (!hook.isEnabled()) {
            logger.debug(
                    "Skipping handler because the hook is disabled for {}/{}",
                    repository.getProject().getKey(),
                    repository.getSlug());
            return;
        }

        logger.debug(
                "Repository hook is enabled; continuing with trigger {}",
                trigger);

        super.run();
    }

    @Override
    BitbucketVariables createBitbucketVariables() {
        return new BitbucketVariables.Builder()
                .populateFromPR(
                        pullRequest,
                        repository,
                        projectKey,
                        trigger,
                        url)
                .build();
    }

    @Override
    boolean validateJob(Job job, BitbucketVariables bitbucketVariables) {
        String prDest = pullRequest != null
                ? pullRequest.getToRef().getDisplayId()
                : "";

        return validatePrDest(job, prDest)
                && validateTrigger(job, trigger)
                && validatePath(job, bitbucketVariables);
    }

    boolean validatePrDest(Job job, String prDest) {
        String prDestRegex = job.getPrDestRegex();
        return prDestRegex.isEmpty()
                || prDest.toLowerCase().matches(prDestRegex.toLowerCase());
    }

    boolean validatePath(Job job, BitbucketVariables bitbucketVariables) {
        String pathRegex = job.getPathRegex();

        if (pathRegex.isEmpty()) {
            return true;
        } else if (pullRequest != null) {
            pullRequestService.streamChanges(
                    new PullRequestChangesRequest.Builder(pullRequest).build(),
                    new AbstractChangeCallback() {
                        @Override
                        public boolean onChange(Change change)
                                throws IOException {
                            return triggerJob(change);
                        }

                        private boolean triggerJob(Change change) {
                            if (change.getPath().toString().matches(pathRegex)) {
                                jenkinsConn.triggerJob(
                                        projectKey,
                                        user,
                                        job,
                                        bitbucketVariables);
                                return false;
                            }
                            return true;
                        }

                        @Override
                        public void onStart(ChangeContext context)
                                throws IOException {
                            // noop
                        }

                        @Override
                        public void onEnd(ChangeSummary summary)
                                throws IOException {
                            // noop
                        }
                    });

            return false;
        }

        return true;
    }
}
