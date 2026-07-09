package com.novakai.orchestrator.engine.executors;

import org.junit.jupiter.api.Test;
import org.apache.sshd.sftp.client.SftpClient;

/**
 * Test to explore SftpClient API
 */
public class SftpStepExecutorTest {

    @Test
    void exploreApi() {
        // This is just for testing what methods are available
        SftpClient client = null; // won't be called, just for compilation check

        // Check method signatures:
        // put(InputStream src, String dest, List<OpenMode> modes)
        // readDir(String path) returns Iterable<DirEntry>
        // get(String path, OutputStream dest)
    }
}
