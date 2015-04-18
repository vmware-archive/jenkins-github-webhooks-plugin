package com.saltstack.jenkins.github.webhooks;

import hudson.Extension;
import hudson.model.Hudson;
import hudson.model.Hudson.MasterComputer;
import hudson.model.Item;
import hudson.model.AbstractProject;
import hudson.model.Project;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.FormValidation;
import hudson.util.SequentialExecutionQueue;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import com.cloudbees.jenkins.GitHubTrigger;
import com.cloudbees.jenkins.GitHubPushTrigger;
import com.cloudbees.jenkins.GitHubRepositoryName;
import com.cloudbees.jenkins.GitHubRepositoryNameContributor;
import org.kohsuke.github.GHHook;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHException;
import org.kohsuke.github.GHRepository;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.saltstack.jenkins.github.webhooks.Payload;
import com.saltstack.jenkins.github.webhooks.PullRequestsCause;

/**
 * Triggers a build when we receive a GitHub post-commit webhook.
 *
 * @author Kohsuke Kawaguchi
 */
public class PullRequestsTrigger extends Trigger<AbstractProject<?,?>> implements GitHubTrigger {

    private static Set<GHEvent> GITHUB_EVENTS = [GHEvent.PULL_REQUEST] as Set;

    @DataBoundConstructor
    public PullRequestsTrigger() {
    }

    /**
     * Called when a POST is made.
     */
    @Deprecated
    public void onPost() {
        onPost("");
    }

    /**
     * Called when a POST is made.
     */
    public void onPost(String payload) {
        LOGGER.log(Level.INFO, "onPost String payload: ${payload}")
        String name = " #"+job.getNextBuildNumber();
        PullRequestsCause cause = new PullRequestsCause(payload)
        if (job.scheduleBuild(cause)) {
            LOGGER.info("SCM pull request changes detected in "+ job.getName()+". Triggering "+name);
        } else {
            LOGGER.info("SCM pull request changes detected in "+ job.getName()+". Job is already in the queue");
        }
    }

    public void onPost(Payload payload) {
        LOGGER.log(Level.INFO, "onPost Payload payload: ${payload}")
        String name = " #"+job.getNextBuildNumber();
        def sender = payload.getSenderDetails()
        LOGGER.log(Level.INFO, "onPost Payload Sender: ${sender}")
        PullRequestsCause cause = new PullRequestsCause(payload.getJSON())
        if (job.scheduleBuild(cause)) {
            LOGGER.info("SCM pull request changes detected in "+ job.getName()+". Triggering "+name);
        } else {
            LOGGER.info("SCM pull request changes detected in "+ job.getName()+". Job is already in the queue");
        }
    }

    /**
     * @deprecated
     *      Use {@link GitHubRepositoryNameContributor#parseAssociatedNames(AbstractProject)}
     */
    public Set<GitHubRepositoryName> getGitHubRepositories() {
        return Collections.emptySet();
    }

    @Override
    public void start(AbstractProject<?,?> project, boolean newInstance) {
        super.start(project, newInstance);
        //if (newInstance && GitHubPushTrigger.getDescriptor().isManagedHook() ) {
        if ( newInstance && Trigger.all().get(GitHubPushTrigger.DescriptorImpl.class).isManageHook() ) {
            registerHooks();
        }
        else if ( newInstance ) {
            LOGGER.log(
                Level.WARNING,
                "The GitHub-Plugin is not managing the webhooks. Manual configuration of the " +
                "hook URL in the GitHub UI is required"
            )
        }
    }

    /**
     * Tries to register hook for current associated job.
     * Useful for using from groovy scripts.
     * @since 1.11.2
     */
    public void registerHooks() {
        final Collection<GitHubRepositoryName> names = GitHubRepositoryNameContributor.parseAssociatedNames(job);

        LOGGER.log(Level.INFO, "Adding GitHub pull request webhooks for {0}", names);

        for (GitHubRepositoryName name : names) {
            for (GHRepository repo : name.resolve()) {
                try {
                    if(createJenkinsHook(repo, new URL(Hudson.getInstance().getRootUrl()+WebHook.get().getUrlName()+'/'))) {
                        break;
                    }
                } catch (Throwable e) {
                    LOGGER.log(Level.WARNING, "Failed to add GitHub pull request webhook for "+name, e);
                }
            }
        }
    }

    @Override
    public void stop() {
        final Collection<GitHubRepositoryName> names = GitHubRepositoryNameContributor.parseAssociatedNames(job);
        for (GitHubRepositoryName name : names) {
            for (GHRepository repo : name.resolve()) {
                try {
                    if(removeJenkinsHook(repo, new URL(Hudson.getInstance().getRootUrl()+WebHook.get().getUrlName()+'/'))) {
                        break;
                    }
                } catch (Throwable e) {
                    LOGGER.log(Level.WARNING, "Failed to add GitHub pull request webhook for "+name, e);
                }
            }
        }
    }

    private boolean createJenkinsHook(GHRepository repo, URL url) {
        LOGGER.log(Level.INFO, "Adding GitHub pull request webhook for ${repo.toString()}");
        GHHook existing_hook = getExistingHook(repo, url)
        if ( existing_hook != null ) {
            LOGGER.log(Level.INFO, "GitHub pull request webhook for ${repo.toString()} ${hook.toString()} already exists");
            return true;
        }
        try {
            GHHook hook = repo.createWebHook(new URL(url.toExternalForm()), GITHUB_EVENTS);
            LOGGER.log(Level.INFO, "Added GitHub pull request webhook for ${repo.toString()} ${hook.toString()}");
            return true;
        } catch (IOException e) {
            throw new GHException("Failed to update jenkins hooks", e);
        } catch (Throwable e) {
            LOGGER.log(Level.SEVERE, "Failed to update jenkins hooks", e)
            return false;
        }
    }

    private void removeJenkinsHook(GHRepository repo, URL url) {
        try {
            GHHook existing_hook = getExistingHook(repo, url)
            if ( existing_hook != null ) {
                existing_hook.delete();
            }
        } catch (IOException e) {
            throw new GHException("Failed to update post-commit web hooks", e);
        }
    }

    private getExistingHook(GHRepository repo, URL url) {
        try {
            String urlExternalForm = url.toExternalForm();
            for (GHHook h : repo.getHooks()) {
                if ( h.getName() != 'web' ) {
                    continue
                }
                if (h.getConfig().get("url").equals(urlExternalForm)) {
                    if ( h.getEvents() as Set == GITHUB_EVENTS ) {
                        return h;
                    }
                }
            }
        } catch (IOException e) {
            throw new GHException("Failed to update post-commit web hooks", e);
        }
        return null
    }

    @Extension
    public static class DescriptorImpl extends TriggerDescriptor {
        private static final Logger LOGGER = Logger.getLogger(DescriptorImpl.class.getName());

        public DescriptorImpl() {
            load();
        }

        @Override
        public boolean isApplicable(Item item) {
            return item instanceof AbstractProject;
        }

        @Override
        public String getDisplayName() {
            return "Build when a pull request is opened/closed/syncronized in GitHub (using web-hooks)";
        }

        /**
         * Returns the URL that GitHub should post.
         */
        public static URL getHookUrl() throws MalformedURLException {
            return new URL(Hudson.getInstance().getRootUrl()+WebHook.get().getUrlName()+'/');
        }

        public static DescriptorImpl get() {
            return Trigger.all().get(DescriptorImpl.class);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(PullRequestsTrigger.class.getName());
}
