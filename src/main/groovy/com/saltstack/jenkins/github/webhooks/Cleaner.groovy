package com.saltstack.jenkins.github.webhooks;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.PeriodicWork;
import hudson.triggers.Trigger;
import hudson.util.TimeUnit2;
import org.kohsuke.github.GHException;
import org.kohsuke.github.GHHook;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHRepository;
import com.cloudbees.jenkins.GitHubRepositoryName;
import com.cloudbees.jenkins.GitHubRepositoryNameContributor;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.saltstack.jenkins.github.webhooks.BranchesTrigger;

/**
 * Removes post-commit hooks from repositories that we no longer care.
 *
 * This runs periodically in a delayed fashion to avoid hitting GitHub too often.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class Cleaner extends PeriodicWork {
    private final Set<GitHubRepositoryName> couldHaveBeenRemoved = new HashSet<GitHubRepositoryName>();

    /**
     * Called when a {@link GitHubPushTrigger} is about to be removed.
     */
    synchronized void onStop(AbstractProject<?,?> job) {
        couldHaveBeenRemoved.addAll(GitHubRepositoryNameContributor.parseAssociatedNames(job));
    }

    @Override
    public long getRecurrencePeriod() {
        return TimeUnit2.MINUTES.toMillis(3);
    }

    @Override
    protected void doRun() throws Exception {
        List<GitHubRepositoryName> names;
        synchronized (this) {// atomically obtain what we need to check
            names = new ArrayList<GitHubRepositoryName>(couldHaveBeenRemoved);
            couldHaveBeenRemoved.clear();
        }

        // subtract all the live repositories
        for (AbstractProject<?,?> job : Hudson.getInstance().getAllItems(AbstractProject.class)) {
            BranchesTrigger branches_trigger = job.getTrigger(BranchesTrigger.class);
            if (branches_trigger!=null) {
                names.removeAll(GitHubRepositoryNameContributor.parseAssociatedNames(job));
            }
        }

        // these are the repos that we are no longer interested.
        // erase our hooks
        OUTER:
        for (GitHubRepositoryName r : names) {
            for (GHRepository repo : r.resolve()) {
                try {
                    removeHook(repo,
                               Trigger.all().get(BranchesTrigger.DescriptorImpl.class).getHookUrl(),
                               [GHEvent.CREATE, GHEvent.DELETE]);
                    LOGGER.fine("Removed a hook from "+r+"");
                    continue OUTER;
                } catch (Throwable e) {
                    LOGGER.log(Level.WARNING,"Failed to remove hook from "+r,e);
                }
            }
        }
    }

    //Maybe we should create a remove hook method in the Github API
    //something like public void removeHook(String name, Map<String,String> config)
    private void removeHook(GHRepository repo, URL url, List<GHEvent> events) {
        try {
            String urlExternalForm = url.toExternalForm();
            for (GHHook h : repo.getHooks()) {
                if ( h.getName() != 'web' ) {
                    continue
                }
                if (h.getConfig().get("url").equals(urlExternalForm)) {
                    if ( repo.getEvents() as Set == events as Set ) {
                        h.delete();
                    }
                }
            }
        } catch (IOException e) {
            throw new GHException("Failed to update post-commit web hooks", e);
        }
    }

    public static Cleaner get() {
        return PeriodicWork.all().get(Cleaner.class);
    }

    private static final Logger LOGGER = Logger.getLogger(Cleaner.class.getName());
}
