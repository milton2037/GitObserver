package com.gitobserver.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateServiceTest {

    @Test
    void detectsNewerVersionsCorrectly() {
        assertTrue(UpdateService.isNewerVersion("v0.2.0", "0.1.0"));
        assertTrue(UpdateService.isNewerVersion("0.1.1", "0.1.0"));
        assertTrue(UpdateService.isNewerVersion("1.0.0", "0.1.0"));
        assertTrue(UpdateService.isNewerVersion("v1.2.3", "1.2.2"));

        assertFalse(UpdateService.isNewerVersion("0.1.0", "0.1.0"));
        assertFalse(UpdateService.isNewerVersion("v0.1.0", "0.1.0"));
        assertFalse(UpdateService.isNewerVersion("0.0.9", "0.1.0"));
        assertFalse(UpdateService.isNewerVersion("v0.0.5", "0.1.0"));
    }

    @Test
    void cleansVersionTagsProperly() {
        assertEquals("0.1.0", UpdateService.cleanVersion("v0.1.0"));
        assertEquals("0.1.0", UpdateService.cleanVersion("V0.1.0"));
        assertEquals("0.1.0", UpdateService.cleanVersion("0.1.0"));
        assertEquals("1.0.0-rc1", UpdateService.cleanVersion("v1.0.0-rc1"));
        assertEquals("", UpdateService.cleanVersion(null));
        assertEquals("", UpdateService.cleanVersion(""));
    }

    @Test
    void handlesNullAndBlankVersionsGracefully() {
        assertFalse(UpdateService.isNewerVersion(null, "0.1.0"));
        assertFalse(UpdateService.isNewerVersion("v0.2.0", null));
        assertFalse(UpdateService.isNewerVersion("", "0.1.0"));
        assertFalse(UpdateService.isNewerVersion("v0.2.0", ""));
    }
}
