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

import com.saltstack.jenkins.github.webhooks.BranchesCause;

/**
 * Triggers a build when we receive a GitHub post-commit webhook.
 *
 * @author Kohsuke Kawaguchi
 */
public class BranchesTrigger extends Trigger<AbstractProject<?,?>> implements GitHubTrigger {
    @DataBoundConstructor
    public BranchesTrigger() {
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
        String name = " #"+job.getNextBuildNumber();
        BranchesCause cause = new BranchesCause(payload.getSender())
        if (job.scheduleBuild(cause)) {
            LOGGER.info("SCM branch changes detected in "+ job.getName()+". Triggering "+name);
        } else {
            LOGGER.info("SCM branch changes detected in "+ job.getName()+". Job is already in the queue");
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
        // make sure we have hooks installed. do this lazily to avoid blocking the UI thread.
        final Collection<GitHubRepositoryName> names = GitHubRepositoryNameContributor.parseAssociatedNames(job);

        LOGGER.log(Level.INFO, "Adding GitHub branch webhooks for {0}", names);

        for (GitHubRepositoryName name : names) {
            for (GHRepository repo : name.resolve()) {
                try {
                    if(createJenkinsHook(repo, new URL(Hudson.getInstance().getRootUrl()+WebHook.get().getUrlName()+'/'))) {
                        break;
                    }
                } catch (Throwable e) {
                    LOGGER.log(Level.WARNING, "Failed to add GitHub branch webhook for "+name, e);
                }
            }
        }
    }

    private boolean createJenkinsHook(GHRepository repo, URL url) {
        LOGGER.log(Level.INFO, "Adding GitHub branch webhook for "+repo.toString());
        Collection<GHEvent> events = new ArrayList();
        events.add(GHEvent.CREATE);
        events.add(GHEvent.DELETE);
        try {
            GHHook hook = repo.createWebHook(new URL(url.toExternalForm()), events);
            LOGGER.log(Level.INFO, "Added GitHub branches webhook for "+repo.toString()+" "+hook.toString());
            return true;
        } catch (IOException e) {
            throw new GHException("Failed to update jenkins hooks", e);
        } catch (Throwable e) {
            LOGGER.log(Level.SEVERE, "Failed to update jenkins hooks", e)
            return false;
        }
    }

    @Override
    public void stop() {
        Cleaner cleaner = Cleaner.get();
        if (cleaner != null) {
            cleaner.onStop(job);
        }
    }

    @Extension
    public static class DescriptorImpl extends TriggerDescriptor {
        private static final Logger LOGGER = Logger.getLogger(DescriptorImpl.class.getName());
        private transient final SequentialExecutionQueue queue = new SequentialExecutionQueue(MasterComputer.threadPoolForRemoting);

        public DescriptorImpl() {
            load();
        }

        @Override
        public boolean isApplicable(Item item) {
            return item instanceof AbstractProject;
        }

        @Override
        public String getDisplayName() {
            return "Build when a branch is created or deleted in GitHub (using web-hooks)";
        }

        /**
         * Returns the URL that GitHub should post.
         */
        public URL getHookUrl() throws MalformedURLException {
            return hookUrl!=null ? new URL(hookUrl) : new URL(Hudson.getInstance().getRootUrl()+WebHook.get().getUrlName()+'/');
        }

        public FormValidation doReRegister() {
            int triggered = 0;
            for (AbstractProject<?,?> job : getJenkinsInstance().getAllItems(AbstractProject.class)) {
                if (!job.isBuildable()) {
                    continue;
                }

                BranchesTrigger trigger = job.getTrigger(BranchesTrigger.class);
                if (trigger!=null) {
                    LOGGER.log(Level.FINE, "Calling registerHooks() for {0}", job.getFullName());
                    trigger.registerHooks();
                    triggered++;
                }
            }

            LOGGER.log(Level.INFO, "Called registerHooks() for {0} jobs", triggered);
            return FormValidation.ok("Called re-register hooks for " + triggered + " jobs");
        }

        public static final Jenkins getJenkinsInstance() throws IllegalStateException {
            Jenkins instance = Jenkins.getInstance();
            if (instance == null) {
                throw new IllegalStateException("Jenkins has not been started, or was already shut down");
            }
            return instance;
        }

        public static DescriptorImpl get() {
            return Trigger.all().get(DescriptorImpl.class);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(BranchesTrigger.class.getName());
}
