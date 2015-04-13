package com.saltstack.jenkins.github.webhooks;

import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.model.AbstractProject;
import hudson.model.UnprotectedRootAction;
import hudson.security.csrf.CrumbExclusion;
import hudson.triggers.Trigger;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContextHolder;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

import java.net.URISyntaxException;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;

import com.cloudbees.jenkins.GitHubTrigger;
import com.cloudbees.jenkins.GitHubRepositoryName;

import com.saltstack.jenkins.github.webhooks.Payload;

@Extension
public class WebHook implements UnprotectedRootAction {

    private static final Logger LOGGER = Logger.getLogger(WebHook.class.getName());
    public static final URLNAME = "github-webhook-payloads";

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return null;
    }

    public String getUrlName() {
        return URLNAME;
    }

    /**
     * Receives the webhook call.
     */
    @RequirePOST
    public void doIndex(StaplerRequest req, StaplerResponse rsp) {

        String eventType = req.getHeader("X-GitHub-Event");
        if ( eventType == null ) {
            throw new IllegalArgumentException("This does not seem to be a github event request")
        }

        // Let GitHub know we got it
        rsp.setStatus(200);

        if ( eventType == 'ping' ) {
            // GitHub is just pinging us
            return
        }

        String json_payload = req.getParameter("payload");
        if (json_payload == null) {
            throw new IllegalArgumentException(
                "Not intended to be browsed interactively (must specify payload parameter). " +
                "Make sure payload version is 'application/vnd.github+form'.");
        }

        Payload payload = new Payload(json_payload, eventType)

        if ( eventType == 'create' || eventType == 'delete' ) {
            processPayload(payload, BranchesTrigger.class);
        }
    }

    private static final Pattern REPOSITORY_NAME_PATTERN = Pattern.compile("https?://([^/]+)/([^/]+)/([^/]+)");

    public void processPayload(Payload payload, Class<? extends Trigger<?>> triggerClass) {
        LOGGER.info("Received POST for "+payload.getRepository());
        LOGGER.fine("Full details of the POST was "+payload.getPayload());
        Matcher matcher = REPOSITORY_NAME_PATTERN.matcher(payload.getRepository());
        if (matcher.matches()) {
            GitHubRepositoryName changedRepository = GitHubRepositoryName.create(payload.getRepository());
            if (changedRepository == null) {
                LOGGER.warning("Malformed repo url "+repoUrl);
                return;
            }

            // run in high privilege to see all the projects anonymous users don't see.
            // this is safe because when we actually schedule a build, it's a build that can
            // happen at some random time anyway.
            Authentication old = SecurityContextHolder.getContext().getAuthentication();
            SecurityContextHolder.getContext().setAuthentication(ACL.SYSTEM);
            try {
                for (AbstractProject<?,?> job : Hudson.getInstance().getAllItems(AbstractProject.class)) {
                    GitHubTrigger trigger = (GitHubTrigger) job.getTrigger(triggerClass);
                    if (trigger!=null) {
                        LOGGER.fine("Considering to poke "+job.getFullDisplayName());
                        if (GitHubRepositoryNameContributor.parseAssociatedNames(job).contains(changedRepository)) {
                            LOGGER.info("Poked "+job.getFullDisplayName());
                            trigger.onPost(payload);
                        } else
                            LOGGER.fine("Skipped "+job.getFullDisplayName()+" because it doesn't have a matching repository.");
                    }
                }
            } finally {
                SecurityContextHolder.getContext().setAuthentication(old);
            }
        } else {
            LOGGER.warning("Malformed repo url "+repoUrl);
        }

    }

    @Extension
    public static class WebHookCrumbExclusion extends CrumbExclusion {

        @Override
        public boolean process(HttpServletRequest req, HttpServletResponse resp, FilterChain chain) throws IOException, ServletException {
            String pathInfo = req.getPathInfo();
            if (pathInfo != null && pathInfo.equals(getExclusionPath())) {
                chain.doFilter(req, resp);
                return true;
            }
            return false;
        }

        public String getExclusionPath() {
            return "/" + URLNAME + "/";
        }
    }
}
