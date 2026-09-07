define(
    'jenkins/parameterized-build-layout',
    [
        'jquery',
        'trigger/build-dialog',
        'bitbucket/util/server',
        '@atlassian/clientside-extensions-registry'
    ],
    function (
        $,
        branchBuild,
        serverUtil,
        registry
    ) {
        'use strict';

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

        function getEligibility(context, ref) {
            var jobsUrl = getResourceUrl(context, 'getJobs');
            var hookUrl = getResourceUrl(context, 'getHookEnabled');

            if (!jobsUrl || !hookUrl) {
                console.error(
                    '[Parameterized Builds] Cannot check layout eligibility: URLs unavailable'
                );

                return $.Deferred()
                    .reject('Missing project/repository context')
                    .promise();
            }

            jobsUrl +=
                '?branch=' +
                encodeURIComponent(ref.id) +
                '&commit=' +
                encodeURIComponent(ref.latestCommit);

            return $.when(
                serverUtil.ajax({
                    type: 'GET',
                    url: jobsUrl,
                    dataType: 'json'
                }),
                serverUtil.ajax({
                    type: 'GET',
                    url: hookUrl,
                    dataType: 'json'
                })
            ).then(function (jobsResponse, hookResponse) {
                var jobs = jobsResponse[0] || [];
                var hookEnabled = hookResponse[0] === true;

                return {
                    enabled: hookEnabled && jobs.length > 0,
                    jobs: jobs,
                    hookEnabled: hookEnabled
                };
            });
        }

        function buttonPluginFactory(pluginAPI, context) {
            var ref = context && context.atRevisionRef;
            var attributes = {
                type: 'button',
                label: 'Build in Jenkins',
                weight: 1000,
                hidden: true,

                onAction: function () {

                    if (!ref || !ref.id || !ref.latestCommit) {
                        console.error(
                            '[Parameterized Builds] Invalid branch-layout ref',
                            {
                                ref: ref,
                                context: context
                            }
                        );
                        return;
                    }

                    branchBuild.openForRef(
                        ref.id,
                        ref.latestCommit
                    );
                }
            };

            if (!ref || !ref.id || !ref.latestCommit) {
                console.warn(
                    '[Parameterized Builds] Hiding layout button: no valid ref',
                    {
                        ref: ref,
                        context: context
                    }
                );

                return attributes;
            }

            getEligibility(context, ref)
                .then(function (result) {

                    if (result.enabled) {
                        pluginAPI.updateAttributes({
                            hidden: false
                        });

                    } else {
                        // nothing to do
                    }
                })
                .fail(function (error) {
                    console.error(
                        '[Parameterized Builds] Layout eligibility request failed',
                        error
                    );
                });

            return attributes;
        }

        registry.registerExtension(
            'com.kylenicholls.stash.parameterized-builds:' +
                'branch-layout-trigger-jenkins',
            buttonPluginFactory
        );
    }
);

require([
    'jenkins/parameterized-build-layout'
]);
