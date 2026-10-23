package dev.leetcodebreak.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import dev.leetcodebreak.model.Site;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.APP)
@State(name = "LeetCodeBreakPreferences", storages = @Storage("leetcode-break.xml"))
public final class PracticeState implements PersistentStateComponent<PracticeState.Data> {
    public static final class Data {
        public String site = Site.CN.name();
        public int minutes = 0;
    }
    private Data data = new Data();
    public static PracticeState getInstance() { return ApplicationManager.getApplication().getService(PracticeState.class); }
    @Override public synchronized Data getState() {
        Data copy = new Data(); copy.site = data.site; copy.minutes = data.minutes;
        return copy;
    }
    @Override public synchronized void loadState(@NotNull Data state) {
        data = state;
    }
    public synchronized Site site() {
        try { return Site.valueOf(data.site); } catch (RuntimeException e) { return Site.CN; }
    }
    public synchronized void setSite(Site site) { data.site = site.name(); }
    public synchronized int minutes() { return data.minutes; }
    public synchronized void setMinutes(int minutes) { data.minutes = minutes; }
}
