package com.saltstack.jenkins.github.webhooks;

import hudson.model.Cause.UserIdCause;

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
        return "Started by GitHub webhook triggered by " + getUserName();
    }
}
