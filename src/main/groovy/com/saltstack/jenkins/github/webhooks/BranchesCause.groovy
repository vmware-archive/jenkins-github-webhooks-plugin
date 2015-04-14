package com.saltstack.jenkins.github.webhooks;

import hudson.Extension;
import hudson.Functions;
import hudson.model.Cause;
import hudson.model.Cause.UserIdCause;
import org.jenkinsci.plugins.buildtriggerbadge.provider.BuildTriggerBadgeProvider;


public class BranchesCause extends UserIdCause {
    /**
     * The name of the user who triggered the event from GitHub.
     */
    private String sender_payload;

    public BranchesCause(String sender_payload) {
        super()
        this.sender_payload = sender_payload;
    }

    @Override
    public String getUserId() {
        return this.sender_payload.id.toString()
    }

    @Override
    public String getUserName() {
        return this.sender_payload.login
    }

    @Override
    public String getShortDescription() {
        return "Started by GitHub branches create/delete webhook triggered by " + getUserName();
    }

    @Extension
    public static class BranchesCauseBadgeProvider extends BuildTriggerBadgeProvider {
        @Override
        public String provideIcon(Cause cause) {
            if (cause instanceof BranchesCause) {
                return hudson.Functions.getResourcePath() + '/plugin/buildtriggerbadge/images/github-push-cause.png';
            }
            return null;
        }
    }
}
