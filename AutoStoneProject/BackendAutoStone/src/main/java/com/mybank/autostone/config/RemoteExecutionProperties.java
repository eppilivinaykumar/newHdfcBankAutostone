package com.mybank.autostone.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Binds autostone.remote.* from application.properties.
 * See application.properties for the full list with defaults/comments.
 */
@Component
@ConfigurationProperties(prefix = "autostone.remote")
@Getter
@Setter
public class RemoteExecutionProperties {

    /** SSH username used to connect to every remote host (POC: one shared account). */
    private String sshUser = "autostone";

    /** Path (on THIS backend machine) to the private key used for SSH auth. */
    private String sshKeyPath = "/home/autostone/.ssh/id_rsa";

    private int sshPort = 22;

    private int connectTimeoutMs = 8000;

    /** Path to the wrapper script on the REMOTE host. */
    private String wrapperPath = "/opt/autostone/wrapper.sh";

    /** Base URL the wrapper's curl callback should hit - this backend's own address. */
    private String callbackBaseUrl = "http://localhost:8081";

    /** Shared secret used by trusted remote wrappers when posting completion callbacks. */
    private String callbackSecret = "change-me";

    /** Secret file deployed on each remote execution host; never pass the secret in a process argument. */
    private String callbackSecretFile = "/etc/autostone/callback-secret";

    /** Any hostName value in this list is executed locally (ProcessBuilder) instead of over SSH. */
    private List<String> localHostnames = List.of("localhost", "127.0.0.1");
}