package com.kylenicholls.stash.parameterizedbuilds.rest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import com.atlassian.bitbucket.auth.AuthenticationContext;
import com.kylenicholls.stash.parameterizedbuilds.ciserver.Jenkins;
import com.kylenicholls.stash.parameterizedbuilds.ciserver.JenkinsConnection;
import com.kylenicholls.stash.parameterizedbuilds.item.Server;

@Path("/projects/{projectKey}")
public class ProjectResource implements ServerService {
    private Jenkins jenkins;
    private final AuthenticationContext authContext;

    @Inject
    public ProjectResource(Jenkins jenkins,
            AuthenticationContext authContext) {
        this.jenkins = jenkins;
        this.authContext = authContext;
    }

    @GET
    @Path("/servers")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public Response getServers(@Context UriInfo ui){
        if (authContext.isAuthenticated()) {
            String projectKey = ui.getPathParameters().getFirst("projectKey");

            List<Map<String, Object>> servers = jenkins.getJenkinsServers(projectKey).stream()
                    .map(x -> createServerMap(x, projectKey))
                    .collect(Collectors.toList());

            return Response.ok(servers).build();
        } else {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
    }

    @POST
    @Path("/servers/validate")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public Response validate(@Context UriInfo ui, Server server){
        if (authContext.isAuthenticated()) {
            String projectKey = ui.getPathParameters().getFirst("projectKey");
            Server oldServer = jenkins.getJenkinsServer(projectKey, server.getAlias());
            server.setToken(getCurrentDefaultToken(oldServer, server));

            JenkinsConnection jenkinsConn = new JenkinsConnection(jenkins);
            String message = jenkinsConn.testConnection(server);

            if(message.equals("Connection successful")){
                return Response.ok(message).build();
            }

            return Response.status(400).entity(message).build();
        } else {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
    }

    @PUT
    @Path("/servers/{serverAlias}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public Response addServer(@Context UriInfo ui, Server server, 
                              @PathParam("serverAlias") String serverAlias){
        if (authContext.isAuthenticated()){
            List<String> errors = sanitizeServerInput(server);
            if (!errors.isEmpty()) {
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("errors", errors);

                return Response.status(422).entity(response).build();
            }

            String projectKey = ui.getPathParameters().getFirst("projectKey");
            Server oldServer = jenkins.getJenkinsServer(projectKey, serverAlias);
            server.setToken(getCurrentDefaultToken(oldServer, server));
            int returnStatus = oldServer == null ? 201 : 200;
            jenkins.saveJenkinsServer(server, projectKey);
            return Response.status(returnStatus).build();
        } else {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
    }

    @DELETE
    @Path("/servers/{serverAlias}")
    public Response removeServer(@Context UriInfo ui){
        if (authContext.isAuthenticated()) {
            String projectKey = ui.getPathParameters().getFirst("projectKey");
            jenkins.saveJenkinsServer(null, projectKey);
            return Response.status(Response.Status.NO_CONTENT).build();
        } else {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
    }

    @PUT
    @Path("/servers/{serverAlias}/userToken")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public Response addUserToken(@Context UriInfo ui, ServerService.Token token){
        if (authContext.isAuthenticated()) {
            String projectKey = ui.getPathParameters().getFirst("projectKey");
            String user = authContext.getCurrentUser().getSlug();
            jenkins.saveUserToken(user, projectKey, token.getToken());
            return Response.status(Response.Status.NO_CONTENT).build();
        } else {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
    }

    @DELETE
    @Path("/servers/{serverAlias}/userToken")
    public Response removeUserToken(@Context UriInfo ui){
        if (authContext.isAuthenticated()) {
            String projectKey = ui.getPathParameters().getFirst("projectKey");
            String user = authContext.getCurrentUser().getSlug();
            jenkins.saveUserToken(user, projectKey, "");
            return Response.status(Response.Status.NO_CONTENT).build();
        } else {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
    }
}
