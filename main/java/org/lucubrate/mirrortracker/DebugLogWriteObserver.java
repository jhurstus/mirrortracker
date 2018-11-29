package org.lucubrate.mirrortracker;

import java.util.List;

/**
 * Class that observes writes to DebugLog.
 */
interface DebugLogWriteObserver {
    void onLogWritten(List<String> logLines);
}
