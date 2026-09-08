define(
    "jenkins/parameterized-build-branchlist",
    [
        "jquery",
        "bitbucket/util/server",
        "trigger/build-dialog",
        "@atlassian/clientside-extensions-registry"
    ],
    function (
        $,
        serverUtil,
        branchBuild,
        registry
    ) {
        "use strict";

        function getResourceUrl(context, resource) {
            var repository = context && context.repository;
            var project = repository && repository.project;

            if (!project || !project.key || !repository || !repository.slug) {
                console.error(
                    "[Parameterized Builds] Missing project/repository information",
                    context
                );
                return null;
            }

            var contextPath =
                typeof AJS !== "undefined" && AJS.contextPath
                    ? AJS.contextPath()
                    : "";

            return contextPath
                + "/rest/parameterized-builds/latest/projects/"
                + encodeURIComponent(project.key)
                + "/repos/"
                + encodeURIComponent(repository.slug)
                + "/"
                + resource;
        }

        function getEligibility(context, branch) {
            var jobsUrl = getResourceUrl(context, "getJobs");
            var hookUrl = getResourceUrl(context, "getHookEnabled");

            return $.when(
                serverUtil.ajax({
                    type: "GET",
                    url: jobsUrl + "?branch="
                        + encodeURIComponent(branch.id)
                        + "&commit="
                        + encodeURIComponent(branch.latestCommit),
                    dataType: "json"
                }),
                serverUtil.ajax({
                    type: "GET",
                    url: hookUrl,
                    dataType: "json"
                })
            ).then(function (jobsResponse, hookResponse) {
                var jobs = jobsResponse[0] || [];
                var hookEnabled = hookResponse[0] === true;

                var result = {
                    enabled: hookEnabled && jobs.length > 0,
                    jobs: jobs,
                    hookEnabled: hookEnabled
                };

                return result;
            });
        }

        function buttonPluginFactory(pluginAPI, context) {
            var branch = context && context.branch;
            var attributes = {
                type: "button",
                label: "Build in Jenkins",
                hidden: true,

                onAction: function () {
                    if (!branch || !branch.id || !branch.latestCommit) {
                        console.error(
                            "[Parameterized Builds] Invalid branch context:",
                            context
                        );
                        return;
                    }

                    branchBuild.openForRef(
                        branch.id,
                        branch.latestCommit
                    );
                }
            };

            if (!branch || !branch.id || !branch.latestCommit) {
                return attributes;
            }

            getEligibility(context, branch)
                .then(function (result) {
                    if (result.enabled) {
                        pluginAPI.updateAttributes({
                            hidden: false
                        });
                    }
                })
                .fail(function (error) {
                    console.error(
                        "[Parameterized Builds] Branch-list eligibility failed:",
                        error
                    );
                });

            return attributes;
        }

        registry.registerExtension(
            "com.kylenicholls.stash.parameterized-builds:"
                + "branch-list-trigger-jenkins",
            buttonPluginFactory
        );
    }
);

require([
    "jenkins/parameterized-build-branchlist"
]);