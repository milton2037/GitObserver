package com.gitobserver.github;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LinkHeaderTest {
    @Test
    void readsNextLink() {
        String header = "<https://api.github.com/user/repos?page=2>; rel=\"next\", "
                + "<https://api.github.com/user/repos?page=5>; rel=\"last\"";
        assertEquals("https://api.github.com/user/repos?page=2", LinkHeader.next(header));
    }

    @Test
    void returnsNullWhenThereIsNoNextPage() {
        assertNull(LinkHeader.next(null));
        assertNull(LinkHeader.next("<https://api.github.com/user/repos?page=2>; rel=\"prev\""));
    }
}
