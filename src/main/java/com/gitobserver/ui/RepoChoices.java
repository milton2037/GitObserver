package com.gitobserver.ui;

import com.gitobserver.AppContext;
import com.gitobserver.github.RepositoryCache;
import com.gitobserver.model.Account;
import com.gitobserver.model.RepoRef;
import javafx.scene.control.ComboBox;

import java.util.ArrayList;
import java.util.List;

public final class RepoChoices {
    public static final String ALL = "Todos los repositorios";

    private RepoChoices() {
    }

    public static void fill(ComboBox<String> combo, List<Account> accounts, RepositoryCache cache) {
        String selected = combo.getValue();
        List<String> names = new ArrayList<>();
        names.add(ALL);
        for (Account account : accounts) {
            List<RepoRef> repos = cache.cachedRepos(account.getId());
            if (repos == null) {
                continue;
            }
            for (RepoRef repo : repos) {
                if (!names.contains(repo.fullName())) {
                    names.add(repo.fullName());
                }
            }
        }
        if (names.size() > 1) {
            names.subList(1, names.size()).sort(String.CASE_INSENSITIVE_ORDER);
        }
        combo.getItems().setAll(names);
        combo.setValue(selected != null && names.contains(selected) ? selected : ALL);
    }

    public static void fillActive(ComboBox<String> combo, AppContext context, boolean allAccounts) {
        List<Account> accounts = new ArrayList<>();
        if (allAccounts) {
            accounts.addAll(context.accounts());
        } else if (context.activeAccount() != null) {
            accounts.add(context.activeAccount());
        }
        fill(combo, accounts, context);
    }

    public static String only(String selected) {
        if (selected == null || ALL.equals(selected)) {
            return null;
        }
        return selected;
    }
}
