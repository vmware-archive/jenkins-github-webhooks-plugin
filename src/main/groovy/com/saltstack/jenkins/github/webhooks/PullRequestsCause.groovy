package com.saltstack.jenkins.github.webhooks;

import hudson.Extension;
import hudson.Functions;
import hudson.model.Cause;
import hudson.model.Cause.UserIdCause;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.jenkinsci.plugins.buildtriggerbadge.provider.BuildTriggerBadgeProvider;


public class PullRequestsCause extends UserIdCause {
    /**
     * The name of the user who triggered the event from GitHub.
     */
    private HashMap payload;

    public PullRequestsCause(HashMap payload) {
        super()
        LOGGER.log(Level.FINE, "Instantiating branches cause with ${payload} of type ${payload.getClass()}")
        this.payload = payload;
    }

    @Override
    public String getUserId() {
        return this.payload.sender.id.toString()
    }

    @Override
    public String getUserName() {
        return this.payload.sender.login
    }

    @Override
    public String getShortDescription() {
        return "Started by GitHub ${getAction()} pull requests webhook event triggered by " + getUserName();
    }

    public String getAction() {
        return this.payload.action
    }

    @Extension
    public static class PullRequestsCauseBadgeProvider extends BuildTriggerBadgeProvider {
        @Override
        public String provideIcon(Cause cause) {
            if (cause instanceof PullRequestsCause) {
                return hudson.Functions.getResourcePath() + '/plugin/buildtriggerbadge/images/github-push-cause.png';
            }
            return null;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(PullRequestsCause.class.getName());
}
