package com.saltstack.jenkins.github.webhooks;

import groovy.json.*;

/**
 * A representation of a GitHub payload
 *
 * @author Pedro Algarvio
 *
 */
public class Payload {

    private event;
    private json;
    private payload;
    private repository;
    private sender;

    /**
     * Instantiate the class with the raw String of JSON
     *
     * @param json The raw JSON string
     */
    public Payload(String json, String event) {
        this.payload = json
        this.json = new JsonSlurper().parseText(json)
        this.event = event;
        this.sender = this.json.sender.login
        this.repository = this.json.repository.full_name
    }

    /**
     * @return the un-parsed JSON payload in case somebody wants it
     */
    public getPayload() {
        return this.payload;
    }

    /**
     * @return the parsed JSONObject in case somebody wants it
     */
    public getJSON() {
        return this.json;
    }

    /**
     * Get the event of the payload
     */
    public getEvent() {
        return this.event
    }

    /**
     * Get the payload author
     */
    public getSender() {
        return this.sender
    }

    /**
     * Get the repository triggering the payload
     */
    public getRepository() {
        return this.repository
    }

    public String toString() {
        return "Payload of event '${this.event}' from repository '${this.repository}' triggered by '${this.sender}'"
    }
}
