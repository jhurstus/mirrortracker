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
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.LinkedBlockingDeque;

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

    private final Deque<String> mLogLines;
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
        mLogLines = new LinkedBlockingDeque<>(MAX_LOG_LINES);

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
        }
    }

    /**
     * @param observer callback invoked whenever log is updated
     */
    void setObserver(DebugLogWriteObserver observer) {
        this.observer = observer;
    }

    List<String> getLogLines() {
        return new ArrayList<>(mLogLines);
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
            for (String line : mLogLines) {
                writer.value(line);
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

    void logServiceStarted() {
        mLogLines.add(timestamp() + "service started");
        write();
    }

    void logServiceStopped() {
        mLogLines.add(timestamp() + "service stopped");
        write();
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

        mLogLines.add(log.toString());
        write();
    }

    void logLocationUpdated(LocationResult result) {
        android.location.Location lastLocation = result.getLastLocation();
        if (lastLocation != null) {
            mLogLines.add(timestamp() +
                    String.format(Locale.getDefault(), "location %3.8f %3.8f",
                            result.getLastLocation().getLatitude(),
                            result.getLastLocation().getLongitude()));
            write();
        }
    }

    void logDbWrite() {
        mLogLines.add(timestamp() + "updated location in firebase");
        write();
    }
}
