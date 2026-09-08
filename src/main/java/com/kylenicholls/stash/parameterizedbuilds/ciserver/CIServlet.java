package com.kylenicholls.stash.parameterizedbuilds.ciserver;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.bitbucket.auth.AuthenticationContext;
import com.atlassian.bitbucket.nav.NavBuilder;
import com.atlassian.bitbucket.project.ProjectService;
import com.atlassian.soy.renderer.SoyException;
import com.atlassian.soy.renderer.SoyTemplateRenderer;
import com.atlassian.webresource.api.assembler.PageBuilderService;

@SuppressWarnings("serial")
public class CIServlet extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(CIServlet.class);
    private final transient SoyTemplateRenderer soyTemplateRenderer;
    private final transient AuthenticationContext authContext;
    private final transient NavBuilder navBuilder;
    private final transient Jenkins jenkins;
    private final transient ProjectService projectService;
    private final transient PageBuilderService pageBuilderService;

    public CIServlet(SoyTemplateRenderer soyTemplateRenderer, AuthenticationContext authContext,
            NavBuilder navBuilder, Jenkins jenkins, ProjectService projectService,
            PageBuilderService pageBuilderService) {
        this.soyTemplateRenderer = soyTemplateRenderer;
        this.authContext = authContext;
        this.navBuilder = navBuilder;
        this.jenkins = jenkins;
        this.projectService = projectService;
        this.pageBuilderService = pageBuilderService;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        try {
            String pathInfo = req.getPathInfo();
            if (authContext.isAuthenticated()) {
                CIServer ciServer = CIServerFactory.getServer(pathInfo, jenkins,
                        authContext.getCurrentUser(), projectService);
                render(res, ciServer.JENKINS_SETTINGS, ciServer.ADDITIONAL_JS,
                         ciServer.renderMap());
            } else {
                res.sendRedirect(navBuilder.login().next(req.getServletPath() + pathInfo)
                        .buildAbsolute());
            }
        } catch (Exception e) {
            logger.error("Exception in CIServlet.doGet: " + e.getMessage(), e);
        }
    }

    private void render(HttpServletResponse resp, String templateName, String addedJs,
                        Map<String, Object> data)
            throws IOException, ServletException {

        pageBuilderService.assembler().resources()
                .requireWebResource(
                        "com.kylenicholls.stash.parameterized-builds:" + addedJs);

        String baseUrl = navBuilder.buildRelative();

        Map<String, Object> renderData = new LinkedHashMap<>();
        if (data != null) {
            renderData.putAll(data);
        }
        renderData.put("bitbucketContext", baseUrl);

        resp.setContentType("text/html;charset=UTF-8");
        try {
            soyTemplateRenderer.render(resp
                    .getWriter(), "com.kylenicholls.stash.parameterized-builds:jenkins-admin-soy",
                    templateName, renderData);
        } catch (SoyException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new ServletException(e);
        }
    }
}