package com.kylenicholls.stash.parameterizedbuilds.eventHandlers;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.setting.Settings;
import com.atlassian.bitbucket.user.ApplicationUser;
import com.kylenicholls.stash.parameterizedbuilds.ciserver.Jenkins;
import com.kylenicholls.stash.parameterizedbuilds.ciserver.JenkinsConnection;
import com.kylenicholls.stash.parameterizedbuilds.helper.SettingsService;
import com.kylenicholls.stash.parameterizedbuilds.item.BitbucketVariables;
import com.kylenicholls.stash.parameterizedbuilds.item.Job;

public abstract class BaseHandler {

    private static final Logger logger =
            LoggerFactory.getLogger(BaseHandler.class);

    final SettingsService settingsService;
    final Jenkins jenkins;
    final JenkinsConnection jenkinsConn;

    Repository repository;
    String projectKey;
    ApplicationUser user;

    public BaseHandler(
            SettingsService settingsService,
            Jenkins jenkins) {
        this.settingsService = settingsService;
        this.jenkins = jenkins;
        this.jenkinsConn = new JenkinsConnection(jenkins);
    }

    public void run() {
        logger.debug(
                "BaseHandler.run entered: handler={}, repository={}/{}, projectKey={}",
                getClass().getSimpleName(),
                repository.getProject().getKey(),
                repository.getSlug(),
                projectKey);

        Settings settings = settingsService.getSettings(repository);

        if (settings == null) {
            logger.warn(
                    "No settings found for repository {}/{}",
                    repository.getProject().getKey(),
                    repository.getSlug());
            return;
        }

        List<Job> jobs = settingsService.getJobs(settings.asMap());

        logger.debug(
                "Loaded {} jobs for repository {}/{}",
                jobs.size(),
                repository.getProject().getKey(),
                repository.getSlug());

        BitbucketVariables variables = createBitbucketVariables();

        for (Job job : jobs) {
            logger.debug(
                    "Evaluating job: id={}, name={}, triggers={}",
                    job.getJobId(),
                    job.getJobName(),
                    job.getTriggers());

            boolean valid = validateJob(job, variables);

            logger.debug(
                    "Job validation result: id={}, name={}, valid={}",
                    job.getJobId(),
                    job.getJobName(),
                    valid);

            if (valid) {
                logger.info(
                        "Triggering Jenkins job: projectKey={}, job={}",
                        projectKey,
                        job.getJobName());

                triggerJenkins(job, variables);
            } else {
                logger.debug(
                        "Skipping Jenkins job: id={}, name={}",
                        job.getJobId(),
                        job.getJobName());
            }
        }
    }

    void triggerJenkins(
            Job job,
            BitbucketVariables bitbucketVariables) {

        logger.info(
                "Calling JenkinsConnection.triggerJob: projectKey={}, job={}, user={}",
                projectKey,
                job.getJobName(),
                user == null ? null : user.getSlug());

        jenkinsConn.triggerJob(
                projectKey,
                user,
                job,
                bitbucketVariables);
    }

    abstract BitbucketVariables createBitbucketVariables();

    abstract boolean validateJob(
            Job job,
            BitbucketVariables bitbucketVariables);

    boolean validateTrigger(Job job, Job.Trigger trigger) {
        boolean result = job.getTriggers().contains(trigger);

        logger.debug(
                "Trigger validation: job={}, configuredTriggers={}, requiredTrigger={}, result={}",
                job.getJobName(),
                job.getTriggers(),
                trigger,
                result);

        return result;
    }

    boolean validateTag(
            Job job,
            boolean isTag) {
        return job.getIsTag() == isTag;
    }

    boolean validateBranch(
            Job job,
            String branch) {
        String branchRegex = job.getBranchRegex();

        return branchRegex.isEmpty()
                || branch.toLowerCase()
                .matches(branchRegex.toLowerCase());
    }
}