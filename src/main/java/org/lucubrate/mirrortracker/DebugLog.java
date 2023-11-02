package org.lucubrate.mirrortracker;

import android.util.JsonReader;
import android.util.JsonWriter;

import com.google.android.gms.location.GeofencingEvent;
import com.google.android.gms.location.LocationResult;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import static com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_DWELL;
import static com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_ENTER;
import static com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_EXIT;

/**
 * Append-only non-volatile debug log.
 */
final class DebugLog {
    final private static String FILE_NAME = "debug_log.json";
    final private static int MAX_LOG_LINES = 100;
    private static DebugLog mDebugLog;

    private final File mLogFile;

    // Circular buffer of log lines, capped at MAX_LOG_LINES size.
    private final List<String> mLogLines;
    // Index of the next slot to be written in circular mLogLines buffer.
    private int mLogIndex;
    private DebugLogWriteObserver observer;

    /**
     * Gets singleton instance of DebugLog.
     * @param fileDir directory to which the debug log file will be written
     */
    static DebugLog getInstance(File fileDir) {
        if (mDebugLog == null) {
            mDebugLog = new DebugLog(fileDir);
        }
        return mDebugLog;
    }


    private DebugLog(File fileDir) {
        mLogLines = Collections.synchronizedList(new ArrayList<>());
        mLogIndex = 0;

        mLogFile = new File(fileDir, FILE_NAME);
        if (!mLogFile.exists()) {
            try {
                //noinspection ResultOfMethodCallIgnored
                mLogFile.createNewFile();
            } catch (IOException ignored) {}
        }
        if (mLogFile.canRead() && mLogFile.length() > 0) {
            try {
                JsonReader reader = new JsonReader(
                        new InputStreamReader(Files.newInputStream(mLogFile.toPath())));
                reader.beginArray();
                while (reader.hasNext()) {
                    mLogLines.add(reader.nextString());
                }
                reader.endArray();
            } catch (IOException ignored) {}
            while (mLogLines.size() > MAX_LOG_LINES) {
                mLogLines.remove(0);
            }
            mLogIndex = mLogLines.size() % MAX_LOG_LINES;
        }
    }

    /**
     * @param observer callback invoked whenever log is updated
     */
    void setObserver(DebugLogWriteObserver observer) {
        this.observer = observer;
    }

    synchronized List<String> getLogLines() {
        if (mLogLines.size() < MAX_LOG_LINES) {
            return new ArrayList<>(mLogLines);
        }
        List<String> ret = new ArrayList<>(mLogLines.size());
        ret.addAll(mLogLines.subList(mLogIndex, mLogLines.size()));
        if (mLogIndex != 0) {
            ret.addAll(mLogLines.subList(0, mLogIndex));
        }
        return ret;
    }

    private void write() {
        if (!mLogFile.canWrite()) {
            return;
        }
        FileOutputStream stream;
        try {
            stream = new FileOutputStream(mLogFile);
        } catch (FileNotFoundException e) {
            return;
        }

        try {
            JsonWriter writer = new JsonWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8));
            writer.setIndent("  ");
            writer.beginArray();
            if (mLogLines.size() < MAX_LOG_LINES) {
                for (String line : mLogLines) {
                    writer.value(line);
                }
            } else {
                for (int i = mLogIndex; i < mLogLines.size(); i++) {
                    writer.value(mLogLines.get(i));
                }
                for (int i = 0; i < mLogIndex; i++) {
                    writer.value(mLogLines.get(i));
                }
            }
            writer.endArray();
            writer.close();
        } catch (IOException ignored) {}

        if (observer != null) {
            observer.onLogWritten(getLogLines());
        }
    }

    private String timestamp() {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("America/Los_Angeles"));
        return String.format(Locale.getDefault(), "%4d-%02d-%02d %02d:%02d:%02d: ",
                c.get(Calendar.YEAR),
                c.get(Calendar.MONTH) + 1,
                c.get(Calendar.DATE),
                c.get(Calendar.HOUR_OF_DAY),
                c.get(Calendar.MINUTE),
                c.get(Calendar.SECOND));
    }

    private synchronized void addLogLine(String line) {
        if (mLogLines.size() < MAX_LOG_LINES) {
            mLogLines.add(line);
        } else {
            mLogLines.set(mLogIndex, line);
        }
        mLogIndex = (mLogIndex + 1) % MAX_LOG_LINES;
        write();
    }

    void logServiceStarted() {
        addLogLine(timestamp() + "service started");
    }

    void logServiceStopped() {
        addLogLine(timestamp() + "service stopped");
    }

    void logGeofencingEvent(GeofencingEvent e) {
        int geofenceTransition = e.getGeofenceTransition();
        StringBuilder log = new StringBuilder(timestamp());
        log.append("geofence");
        if (geofenceTransition == GEOFENCE_TRANSITION_ENTER) {
            log.append(" enter");
        } else if (geofenceTransition == GEOFENCE_TRANSITION_EXIT) {
            log.append(" exit");
        } else if (geofenceTransition == GEOFENCE_TRANSITION_DWELL) {
            log.append(" dwell");
        }
        List<com.google.android.gms.location.Geofence> triggeringGeofences =
                e.getTriggeringGeofences();
        if (triggeringGeofences != null) {
            for (com.google.android.gms.location.Geofence g : triggeringGeofences) {
                log.append(' ');
                log.append(g.getRequestId());
            }
        }

        addLogLine(log.toString());
    }

    void logLocationUpdated(LocationResult result) {
        android.location.Location lastLocation = result.getLastLocation();
        if (lastLocation != null) {
            addLogLine(timestamp() +
                    String.format(Locale.getDefault(), "location %3.8f %3.8f",
                            result.getLastLocation().getLatitude(),
                            result.getLastLocation().getLongitude()));
        }
    }

    void logDbWrite() {
        addLogLine(timestamp() + "updated location in firebase");
    }
}
