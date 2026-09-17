package com.nutriflow.app.analysis;

/** A stable detail opens once per visit, including after the sheet is dismissed. */
public final class DetailVisitTracker {
    private String candidate = "";
    private String shown = "";
    private long candidateSince;
    private long absentSince = -1;

    public boolean observe(String key, long now) {
        if (key == null || key.isEmpty()) {
            candidate = "";
            if (absentSince < 0) absentSince = now;
            if (now - absentSince >= 900) shown = "";
            return false;
        }
        absentSince = -1;
        if (!key.equals(candidate)) { candidate = key; candidateSince = now; return false; }
        return !key.equals(shown) && now - candidateSince >= 650;
    }

    public void markShown(String key) { shown = key; }
    public void reset() { candidate = ""; shown = ""; absentSince = -1; }
}
