package com.kylenicholls.stash.parameterizedbuilds.rest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import com.atlassian.bitbucket.repository.RepositoryService;
import com.atlassian.bitbucket.auth.AuthenticationContext;
import com.atlassian.bitbucket.hook.repository.RepositoryHook;
import com.atlassian.bitbucket.pull.PullRequest;
import com.atlassian.bitbucket.pull.PullRequestService;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.server.ApplicationPropertiesService;
import com.atlassian.bitbucket.setting.Settings;
import com.atlassian.bitbucket.user.ApplicationUser;
import com.kylenicholls.stash.parameterizedbuilds.ciserver.Jenkins;
import com.kylenicholls.stash.parameterizedbuilds.ciserver.JenkinsConnection;
import com.kylenicholls.stash.parameterizedbuilds.conditions.BuildPermissionsCondition;
import com.kylenicholls.stash.parameterizedbuilds.helper.SettingsService;
import com.kylenicholls.stash.parameterizedbuilds.item.BitbucketVariables;
import com.kylenicholls.stash.parameterizedbuilds.item.BitbucketVariables.Builder;
import com.kylenicholls.stash.parameterizedbuilds.item.Job;
import com.kylenicholls.stash.parameterizedbuilds.item.Job.Trigger;
import com.kylenicholls.stash.parameterizedbuilds.item.Server;

@Path("/projects/{projectKey}/repos/{repositorySlug}")
@Consumes({ MediaType.APPLICATION_JSON })
@Produces({ MediaType.APPLICATION_JSON })
@Deprecated
public class BuildResource {
    private SettingsService settingsService;
    private Jenkins jenkins;
    private final ApplicationPropertiesService applicationPropertiesService;
    private final PullRequestService prService;
    private final AuthenticationContext authContext;
    private final BuildPermissionsCondition permissionsCheck;
    private final RepositoryService repositoryService;

    @Inject
    public BuildResource(SettingsService settingsService,
                         Jenkins jenkins,
                         ApplicationPropertiesService applicationPropertiesService,
                         PullRequestService prService,
                         AuthenticationContext authContext,
                         BuildPermissionsCondition permissionsCheck,
                         RepositoryService repositoryService) {
        this.settingsService = settingsService;
        this.jenkins = jenkins;
        this.applicationPropertiesService = applicationPropertiesService;
        this.prService = prService;
        this.authContext = authContext;
        this.permissionsCheck = permissionsCheck;
        this.repositoryService = repositoryService;
    }

    @POST
    @Path("triggerBuild/{id}")
    public Response triggerBuild(
            @PathParam("projectKey") String projectKey,
            @PathParam("repositorySlug") String repositorySlug,
            @PathParam("id") String id,
            @QueryParam("branch") String branch,
            @Context UriInfo uriInfo) {

        if (!authContext.isAuthenticated()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        if (branch == null || branch.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("message", "Branch is required"))
                    .build();
        }

        Repository repository = getRepository(projectKey, repositorySlug);

        if (repository == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Map<String, Object> data = new LinkedHashMap<>();
        Settings settings = settingsService.getSettings(repository);

        if (settings == null) {
            data.put("message", "No build settings were found for this repository");
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(data)
                    .build();
        }

        List<Job> jobs = settingsService.getJobs(settings.asMap());
        Job jobToBuild = getJobById(Integer.parseInt(id), jobs);

        if (jobToBuild == null) {
            data.put("message", "No settings found for this job");
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(data)
                    .build();
        }

        ApplicationUser user = authContext.getCurrentUser();

        Map<String, Object> paramList = uriInfo.getQueryParameters()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Entry::getKey,
                        entry -> entry.getValue().get(0)));

        Job job = jobToBuild.copy()
                .buildParameters(paramList)
                .build();

        BitbucketVariables variables = new BitbucketVariables.Builder()
                .add("$BRANCH", () -> branch)
                .add("$TRIGGER", Trigger.MANUAL::toString)
                .build();

        JenkinsConnection jenkinsConnection =
                new JenkinsConnection(jenkins);

        Map<String, Object> message = jenkinsConnection
                .triggerJob(projectKey, user, job, variables)
                .getMessage();

        return Response.ok(message).build();
    }

    @GET
    @Path("getJenkinsServers")
    public Response getJenkinsServers(
            @PathParam("projectKey") String projectKey,
            @PathParam("repositorySlug") String repositorySlug) {

        if (!authContext.isAuthenticated()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        List<Map<String, Object>> servers = new ArrayList<>(
                jenkins.getJenkinsServers(null)
                        .stream()
                        .map(server -> createServerMap(server, null))
                        .toList());

        List<Map<String, Object>> projectServers =
                jenkins.getJenkinsServers(projectKey)
                        .stream()
                        .map(server -> createServerMap(server, projectKey))
                        .toList();

        servers.addAll(projectServers);

        return Response.ok(servers).build();
    }

    private Map<String, Object> createServerMap(Server server, String projectKey){
        Map<String, Object> serverMap = new HashMap<>();
        serverMap.put("url", server.getBaseUrl());
        serverMap.put("alias", server.getAlias());
        serverMap.put("scope", projectKey == null ? "global": "project");
        serverMap.put("project", projectKey);
        serverMap.put("default_user", server.getUser());
        return serverMap;
    }

    @GET
    @Path("getJobs")
    public Response getJobs(
            @PathParam("projectKey") String projectKey,
            @PathParam("repositorySlug") String repositorySlug,
            @QueryParam("branch") String branch,
            @QueryParam("commit") String commit,
            @QueryParam("prdestination") String prDestination,
            @QueryParam("prid") long prId) {

        if (!authContext.isAuthenticated()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        Repository repository = getRepository(projectKey, repositorySlug);

        if (repository == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Settings settings = settingsService.getSettings(repository);

        if (settings == null) {
            return Response.ok(new ArrayList<>()).build();
        }

        String repositoryProjectKey = repository.getProject().getKey();
        String url = applicationPropertiesService.getBaseUrl().toString();

        Builder variableBuilder = new BitbucketVariables.Builder()
                .populateFromStrings(
                        branch,
                        commit,
                        repository,
                        repositoryProjectKey,
                        Trigger.MANUAL,
                        url);

        if (prDestination != null) {
            PullRequest pullRequest =
                    prService.getById(repository.getId(), prId);

            if (pullRequest != null) {
                variableBuilder.populateFromPR(
                        pullRequest,
                        repository,
                        repositoryProjectKey,
                        Trigger.MANUAL,
                        url);
            }
        }

        List<Map<String, Object>> data = new ArrayList<>();

        for (Job job : settingsService.getJobs(settings.asMap())) {
            if (job.getTriggers().contains(Trigger.MANUAL)
                    && permissionsCheck.checkPermissions(
                    job,
                    repository,
                    authContext.getCurrentUser())) {
                data.add(job.asMap(variableBuilder.build()));
            }
        }

        return Response.ok(data).build();
    }

    @GET
    @Path("getHookEnabled")
    public Response getHookEnabled(
            @PathParam("projectKey") String projectKey,
            @PathParam("repositorySlug") String repositorySlug) {

        if (!authContext.isAuthenticated()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        Repository repository = getRepository(projectKey, repositorySlug);

        if (repository == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        RepositoryHook hook = settingsService.getHook(repository);

        if (hook == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(hook.isEnabled()).build();
    }

    @Nullable
    private Job getJobById(int id, List<Job> jobs) {
        for (Job job : jobs) {
            if (job.getJobId() == id) {
                return job;
            }
        }
        return null;
    }

    private Repository getRepository(
            String projectKey,
            String repositorySlug) {
        return repositoryService.getBySlug(projectKey, repositorySlug);
    }
}
