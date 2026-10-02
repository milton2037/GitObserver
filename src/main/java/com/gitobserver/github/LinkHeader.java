package com.gitobserver.github;

public final class LinkHeader {
    private LinkHeader() {
    }

    public static String next(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        for (String part : header.split(",")) {
            String[] pieces = part.split(";");
            if (pieces.length < 2) {
                continue;
            }
            boolean isNext = false;
            for (int index = 1; index < pieces.length; index++) {
                if (pieces[index].trim().equals("rel=\"next\"")) {
                    isNext = true;
                    break;
                }
            }
            if (!isNext) {
                continue;
            }
            String url = pieces[0].trim();
            if (url.startsWith("<") && url.endsWith(">") && url.length() > 2) {
                return url.substring(1, url.length() - 1);
            }
        }
        return null;
    }
}
