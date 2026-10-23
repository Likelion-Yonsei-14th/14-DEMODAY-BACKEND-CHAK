package com.likelion.chak.config;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** RDS 관리 시크릿({"username":"…","password":"…"})의 DB 계정 정보. */
record DbCredentials(String username, String password) {

    private static final ObjectMapper JSON = new ObjectMapper();

    static DbCredentials parse(String json) {
        JsonNode root = JSON.readTree(json);
        String username = root.path("username").asString("");
        String password = root.path("password").asString("");
        if (username.isBlank() || password.isEmpty()) {
            throw new IllegalStateException("DB secret has no username/password");
        }
        return new DbCredentials(username, password);
    }

    @Override
    public String toString() {
        return "DbCredentials[username=" + username + ", password=***]";
    }
}
