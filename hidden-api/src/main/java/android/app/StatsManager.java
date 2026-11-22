package android.app;

import android.util.StatsEvent;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Fugueti: compile-only stub of the statsd APEX API used for launcher snapshot logging. The
 * real class is provided by the device at runtime (Android 11+); callers guard older versions.
 */
public final class StatsManager {
    public static final int PULL_SUCCESS = 0;
    public static final int PULL_SKIP = 1;

    private StatsManager() {}

    public void setPullAtomCallback(int atomTag, PullAtomMetadata metadata, Executor executor,
            StatsPullAtomCallback callback) {
        throw new UnsupportedOperationException("stub");
    }

    public void clearPullAtomCallback(int atomTag) {
        throw new UnsupportedOperationException("stub");
    }

    public interface StatsPullAtomCallback {
        int onPullAtom(int atomTag, List<StatsEvent> data);
    }

    public static final class PullAtomMetadata {
        private PullAtomMetadata() {}
    }
}
