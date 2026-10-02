package com.gitobserver.github;

import com.gitobserver.model.RepoRef;

import java.util.List;

public interface RepositoryCache {
    List<RepoRef> cachedRepos(String accountId);

    void cacheRepos(String accountId, List<RepoRef> repos);

    void invalidateRepos(String accountId);
}
