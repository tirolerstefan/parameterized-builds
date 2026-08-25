package com.kylenicholls.stash.parameterizedbuilds.rest;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.kylenicholls.stash.parameterizedbuilds.item.Server;

import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.apache.http.client.utils.URIBuilder;

public interface ServerService {

    class Token {
        private String token;

        public Token(){}

        public void setToken(String token){
            this.token = token;
        }

        public String getToken(){
            return token;
        }
    }

    Response getServers(@Context UriInfo ui);

    Response validate(@Context UriInfo ui, Server server);

    Response addServer(@Context UriInfo ui, Server server,
                              @PathParam("serverAlias") String serverAlias);

    Response removeServer(@Context UriInfo ui);

    Response addUserToken(@Context UriInfo ui, Token token);

    Response removeUserToken(@Context UriInfo ui);

    default Map<String, Object> createServerMap(Server server, String projectKey){
        Map<String, Object> serverMap = new HashMap<>();
        serverMap.put("url", server.getBaseUrl());
        serverMap.put("alias", server.getAlias());
        serverMap.put("scope", projectKey == null ? "global": "project");
        serverMap.put("project", projectKey);
        serverMap.put("default_user", server.getUser());
        serverMap.put("root_token_enabled", server.getAltUrl());
        serverMap.put("csrf_enabled", server.getCsrfEnabled());
        return serverMap;
    }

    default List<String> sanitizeServerInput(Server server){
        List<String> errors = new ArrayList<>(2);
        if (server.getBaseUrl() == null || server.getBaseUrl().isEmpty()){
            errors.add("Base Url required.");
        } else {
            URIBuilder builder;
            try {
                builder = new URIBuilder(server.getBaseUrl());
                if (builder.getHost() == null){
                    errors.add("Invalide Base Url.");
                }
            } catch (URISyntaxException e) {
                errors.add("Invalide Base Url.");
            }
        }

        if (server.getAlias() == null || server.getAlias().isEmpty()){
            errors.add("Alias required.");
        } else if (server.getAlias().contains("/")) {
            errors.add("Alias cannot include \"/\"");
        }

        return errors;
    }

    default Server mapToServer(Map<String, Object> serverMap){
        return new Server(serverMap);
    }

    default String getCurrentDefaultToken(Server oldServer, Server newServer){
        // if the new server didn't edit the token attribute and the server
        // credentials should be the same, save the old token
        if (shouldUseOldToken(oldServer, newServer)) {
            return oldServer.getToken();
        } else if (newServer.getToken() == null) {
            return "";
        } else {
            return newServer.getToken();
        }
    }

    default boolean shouldUseOldToken(Server oldServer, Server newServer){
        return
            oldServer != null &&
            newServer.getToken() == null &&
            oldServer.getBaseUrl().equals(newServer.getBaseUrl()) &&
            oldServer.getUser().equals(newServer.getUser());
    }

}