package com.example.p2pmessenger;

/** Central place for the backend URL. Use HTTPS in production. */
public final class ServerConfig {
    private ServerConfig() {}
    public static final String BASE_URL = "http://192.168.0.121:8080";
    /** All members of the same physical mesh use the same group id. */
    public static final String MESH_GROUP_ID = "default-mesh-group";
}
