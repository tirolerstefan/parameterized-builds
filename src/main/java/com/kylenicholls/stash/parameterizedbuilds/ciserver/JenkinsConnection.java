package com.kylenicholls.stash.parameterizedbuilds.ciserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import javax.annotation.Nullable;
import javax.net.ssl.SSLException;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.bitbucket.user.ApplicationUser;
import com.kylenicholls.stash.parameterizedbuilds.item.BitbucketVariables;
import com.kylenicholls.stash.parameterizedbuilds.item.JenkinsResponse;
import com.kylenicholls.stash.parameterizedbuilds.item.Job;
import com.kylenicholls.stash.parameterizedbuilds.item.Server;
import com.kylenicholls.stash.parameterizedbuilds.item.JenkinsResponse.JenkinsMessage;

public class JenkinsConnection {

    private static final Logger logger = LoggerFactory.getLogger(JenkinsConnection.class);
    private final Jenkins jenkins;

    public JenkinsConnection(Jenkins jenkins) {
        this.jenkins = jenkins;
    }

    /**
     * Returns a message object from the triggered job.
     *
     * @return a message object from the triggered job.
     * @param buildUrl
     *            the build url to trigger
     * @param joinedToken
     *            the authentication token to use in the request
     * @param csrfHeader
     *            the token to use in case cross site protection is enabled
     * @param promptUser
     *            prompt the user to link their jenkins account
     */
    public JenkinsResponse sanitizeTrigger(
            @Nullable String buildUrl,
            @Nullable String joinedToken,
            @Nullable String csrfHeader,
            boolean promptUser) {

        logger.debug(
                "sanitizeTrigger called: buildUrl={}, tokenPresent={}, "
                        + "csrfHeaderPresent={}, promptUser={}",
                buildUrl,
                joinedToken != null && !joinedToken.isEmpty(),
                csrfHeader != null && !csrfHeader.isEmpty(),
                promptUser);

        if (buildUrl == null) {
            logger.error("Jenkins build URL is null");
            return new JenkinsResponse.JenkinsMessage()
                    .error(true)
                    .messageText("Jenkins settings are not setup")
                    .build();
        }

        return httpPost(
                buildUrl.replace(" ", "%20"),
                joinedToken,
                csrfHeader,
                promptUser);
    }

    public JenkinsResponse triggerJob(
            String projectKey,
            ApplicationUser user,
            Job job,
            BitbucketVariables bitbucketVariables) {

        String configuredAlias = job.getJenkinsServer();

        logger.debug(
                "triggerJob entered: projectKey={}, jobName={}, jobId={}, "
                        + "configuredJenkinsServer={}, trigger={}",
                projectKey,
                job.getJobName(),
                job.getJobId(),
                configuredAlias,
                bitbucketVariables.fetch("$TRIGGER"));

        Server jenkinsServer = null;

        if (configuredAlias != null && !configuredAlias.isEmpty()) {
            logger.debug(
                    "Resolving explicitly configured Jenkins server: "
                            + "projectKey={}, alias={}",
                    projectKey,
                    configuredAlias);

            /*
             * First try a project-scoped server with this alias.
             */
            jenkinsServer = jenkins.getJenkinsServer(
                    projectKey,
                    configuredAlias,
                    user);

            /*
             * If the alias is global, project lookup returns null.
             * Retry with a null project key.
             */
            if (jenkinsServer == null) {
                logger.debug(
                        "No project Jenkins server found for alias={}; "
                                + "trying global server",
                        configuredAlias);

                jenkinsServer = jenkins.getJenkinsServer(
                        null,
                        configuredAlias,
                        user);
            }
        } else {
            logger.debug(
                    "No explicit Jenkins server configured; "
                            + "trying project server for projectKey={}",
                    projectKey);

            jenkinsServer = jenkins.getJenkinsServer(
                    projectKey,
                    null,
                    user);

            if (jenkinsServer == null) {
                logger.debug(
                        "No project Jenkins server found; trying global server");

                jenkinsServer = jenkins.getJenkinsServer(
                        null,
                        null,
                        user);
            }
        }

        if (jenkinsServer == null) {
            logger.error(
                    "No Jenkins server could be resolved: projectKey={}, "
                            + "configuredServer={}, user={}",
                    projectKey,
                    configuredAlias,
                    user == null ? null : user.getSlug());

            return new JenkinsResponse.JenkinsMessage()
                    .error(true)
                    .messageText("Jenkins server not found")
                    .build();
        }

        logger.debug(
                "Resolved Jenkins server: alias={}, baseUrl={}, "
                        + "configuredUser={}, effectiveUser={}",
                jenkinsServer.getAlias(),
                jenkinsServer.getBaseUrl(),
                jenkinsServer.getUser(),
                user == null ? null : user.getSlug());

        String buildUrl = job.buildUrl(
                jenkinsServer,
                bitbucketVariables,
                false);

        logger.debug(
                "Calculated Jenkins build URL for job={}: {}",
                job.getJobName(),
                buildUrl);

        if (buildUrl == null) {
            logger.error(
                    "Job.buildUrl returned null: jobName={}, serverAlias={}",
                    job.getJobName(),
                    jenkinsServer.getAlias());

            return new JenkinsResponse.JenkinsMessage()
                    .error(true)
                    .messageText("Unable to create Jenkins build URL")
                    .build();
        }

        boolean prompt = user == null
                || !jenkinsServer.getUser().equals(user.getSlug());

        String csrfHeader = null;

        if (jenkinsServer.getCsrfEnabled()) {
            logger.debug(
                    "Jenkins CSRF protection is enabled; "
                            + "requesting crumb from {}",
                    jenkinsServer.getBaseUrl());

            try {
                csrfHeader = getCrumb(jenkinsServer);
            } catch (Exception e) {
                logger.warn(
                        "Unable to obtain Jenkins CSRF crumb from {}",
                        jenkinsServer.getBaseUrl(),
                        e);
            }
        }

        logger.debug(
                "Calling sanitizeTrigger: job={}, promptUser={}, "
                        + "csrfHeaderPresent={}",
                job.getJobName(),
                prompt,
                csrfHeader != null);

        return sanitizeTrigger(
                buildUrl,
                jenkinsServer.getJoinedToken(),
                csrfHeader,
                prompt);
    }

    private HttpURLConnection setupConnection(String baseUrl, String userToken) throws Exception{
        URL url = new URL(baseUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        if (userToken != null && !userToken.isEmpty()) {
            byte[] authEncBytes = Base64.encodeBase64(userToken.getBytes());
            String authStringEnc = new String(authEncBytes);
            connection.setRequestProperty("Authorization", "Basic " + authStringEnc);
        }
        connection.setReadTimeout(45000);
        connection.setInstanceFollowRedirects(true);
        connection.setDoOutput(true);
        HttpURLConnection.setFollowRedirects(true);
        return connection;
    }

    public String testConnection(Server server){
        try {
            String url = server.getBaseUrl().replaceAll("/$", "") + "/api/json";
            HttpURLConnection connection = setupConnection(url, server.getJoinedToken());
            connection.setRequestMethod("GET");
            connection.setFixedLengthStreamingMode(0);

            String csrfHeader = null;
            if (server.getCsrfEnabled()) {
                // get a CSRF token because cross site protection is enabled
                try {
                    csrfHeader = getCrumb(server);
                } catch(Exception e){
                    logger.warn("error getting CSRF token");
                }
            }

            if (csrfHeader != null){
                String[] header = csrfHeader.split(":");
                connection.setRequestProperty(header[0], header[1]);
            }

            connection.connect();

            int status = connection.getResponseCode();
            if (status == 403){
                return "Error authenticating to server";
            } else if (status == 200) {
                return "Connection successful";
            } else {
                return "Failed to establish connection";
            }
        } catch (Exception e) {
            return "An error occurred trying to establish a connection";
        }
    }

    private String getCrumb(Server server) throws Exception{
        final String crumbPath = "/crumbIssuer/api/xml?xpath=" + 
                                 "concat(//crumbRequestField,\":\",//crumb)";
        String baseUrl = server.getBaseUrl();
        String token = server.getJoinedToken();
        final String crumbUrl = baseUrl + crumbPath;
        // workaround temporary javax.net.ssl.SSLException: Received close_notify during handshake
        // retry the connection three times should be OK for temporary connection issues
        final int maxRetries = 3;
        final int sleepRetryMS = 3000;
        for( int retry = 1; retry <= maxRetries; ++retry ) {
            try {
                final HttpURLConnection connection = setupConnection(crumbUrl, token);
                connection.connect();
                final int status = connection.getResponseCode();
                if (status == 200) {
                    return new BufferedReader(new InputStreamReader(
                            (connection.getInputStream()))).readLine();
                } else {
                    logger.debug(
                            "Jenkins crumb request returned status={} for {}",
                            status,
                            baseUrl);
                    return null;
                }
            } catch(final SSLException e) {
                if( retry < maxRetries ) {
                    // log issue and try again
                    logger.warn("Could not connect to " + baseUrl +
                                ", will retry in " + sleepRetryMS + "ms", e);
                } else {
                    throw e;
                }
            }
            // wait before next retry
            Thread.sleep(sleepRetryMS);
        }
        return null;
    }

    private JenkinsResponse httpPost(
            String buildUrl,
            String token,
            String csrfHeader,
            boolean prompt) {

        JenkinsMessage jenkinsMessage =
                new JenkinsResponse.JenkinsMessage()
                        .prompt(prompt);

        logger.debug(
                "Starting Jenkins HTTP POST for URL={}",
                buildUrl);

        try {
            HttpURLConnection connection = setupConnection(buildUrl, token);
            connection.setRequestMethod("POST");
            connection.setFixedLengthStreamingMode(0);

            if (csrfHeader != null && !csrfHeader.isEmpty()) {
                String[] header = csrfHeader.split(":", 2);

                if (header.length == 2) {
                    connection.setRequestProperty(
                            header[0].trim(),
                            header[1].trim());

                    logger.debug(
                            "Added Jenkins CSRF header: field={}",
                            header[0].trim());
                } else {
                    logger.warn(
                            "Ignoring malformed Jenkins CSRF header");
                }
            }

            connection.connect();

            int status = connection.getResponseCode();
            String responseMessage = connection.getResponseMessage();

            logger.debug(
                    "Received Jenkins response: status={}, message={}",
                    status,
                    responseMessage);

            if (status == HttpURLConnection.HTTP_CREATED) {
                logger.info(
                        "Jenkins job triggered successfully: status={}",
                        status);

                return jenkinsMessage
                        .messageText("Build triggered")
                        .build();
            }

            if (status == HttpURLConnection.HTTP_FORBIDDEN) {
                logger.warn(
                        "Jenkins rejected the build request: status=403");

                return jenkinsMessage
                        .error(true)
                        .messageText(
                                "You do not have permissions to build this job")
                        .build();
            }

            if (status == HttpURLConnection.HTTP_MOVED_TEMP
                    && "Found".equalsIgnoreCase(responseMessage)) {
                logger.info(
                        "Jenkins accepted the build request with a redirect: status={}",
                        status);

                return jenkinsMessage
                        .messageText("Build triggered")
                        .build();
            }

            if (status == HttpURLConnection.HTTP_NOT_FOUND) {
                logger.warn(
                        "Jenkins job was not found: status=404");

                return jenkinsMessage
                        .error(true)
                        .messageText("Job was not found")
                        .build();
            }

            if (status == HttpURLConnection.HTTP_INTERNAL_ERROR) {
                logger.error(
                        "Jenkins returned HTTP 500 while triggering the job");

                return jenkinsMessage
                        .error(true)
                        .messageText(
                                "Error triggering job, invalid build parameters")
                        .build();
            }

            String message = responseMessage == null
                    ? "Unknown response from Jenkins"
                    : responseMessage;

            logger.error(
                    "Unexpected Jenkins response: status={}, message={}",
                    status,
                    message);

            return jenkinsMessage
                    .error(true)
                    .messageText(message)
                    .build();

        } catch (MalformedURLException e) {
            logger.error(
                    "Malformed Jenkins build URL",
                    e);

            return jenkinsMessage
                    .error(true)
                    .messageText("Malformed URL: " + e.getMessage())
                    .build();

        } catch (IOException e) {
            logger.error(
                    "I/O error while sending Jenkins request",
                    e);

            return jenkinsMessage
                    .error(true)
                    .messageText("IO exception occurred: " + e.getMessage())
                    .build();

        } catch (Exception e) {
            logger.error(
                    "Unexpected error while sending Jenkins request",
                    e);

            return jenkinsMessage
                    .error(true)
                    .messageText("Something went wrong: " + e.getMessage())
                    .build();
        }
    }
}