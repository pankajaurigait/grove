package com.samagra.grove.logging;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;

import android.os.Bundle;
import android.util.Log;

import com.samagra.grove.contracts.ErrorActivityHandler;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import io.sentry.Sentry;
import io.sentry.event.Breadcrumb;

public class UCEHandler {

    public static final String EXTRA_STACK_TRACE = "EXTRA_STACK_TRACE";
    public static final String EXTRA_ACTIVITY_LOG = "EXTRA_ACTIVITY_LOG";
    public static final String ACTIVITY_NAME = "ACTIVITY_NAME";
    private final static String TAG = "UCEHandler";
    // private static final String UCE_HANDLER_PACKAGE_NAME = "org.odk.collect.android.mSamvaad";
    private static final String UCE_HANDLER_PACKAGE_NAME = "com.himachal.android.eSamwad";
    private static final String DEFAULT_HANDLER_PACKAGE_NAME = "com.android.internal.os";
    private static final int MAX_STACK_TRACE_SIZE = 131071; //128 KB - 1
    private static final int MAX_ACTIVITIES_IN_LOG = 100;
    private static final String SHARED_PREFERENCES_FILE = "uceh_preferences";
    private static final String SHARED_PREFERENCES_FIELD_TIMESTAMP = "last_crash_timestamp";
    private static final Deque<String> activityLog = new ArrayDeque<>(MAX_ACTIVITIES_IN_LOG);
    static String COMMA_SEPARATED_EMAIL_ADDRESSES;
    @SuppressLint("StaticFieldLeak")
    private static Application application;
    private static boolean isInBackground = true;
    private static boolean isBackgroundMode;
    private static boolean isUCEHEnabled;
    private static ErrorActivityHandler errorHandler;
    private static boolean isTrackActivitiesEnabled;
    private static WeakReference<Activity> lastActivityCreated = new WeakReference<>(null);
    private static String senderEmail;
    private static String receiverEmail;

    private UCEHandler(Builder builder) {
        application = builder.applicationInstance;
        errorHandler = builder.errorActivityHandler;
        isUCEHEnabled = builder.isUCEHEnabled;
        isTrackActivitiesEnabled = builder.isTrackActivitiesEnabled;
        isBackgroundMode = builder.isBackgroundModeEnabled;
        senderEmail = builder.sendingUserEmailId;
        receiverEmail = builder.receivingUserEmailId;
        COMMA_SEPARATED_EMAIL_ADDRESSES = builder.commaSeparatedEmailAddresses;
        setUCEHandler(builder.context);
    }

    private static void setUCEHandler(final Context context) {
        try {
            if (context != null) {
                final Thread.UncaughtExceptionHandler oldHandler = Thread.getDefaultUncaughtExceptionHandler();
                if (oldHandler != null && oldHandler.getClass().getName().startsWith(UCE_HANDLER_PACKAGE_NAME)) {
                    Log.e(TAG, "UCEHandler was already installed, doing nothing!");
                } else {
                    if (oldHandler != null && !oldHandler.getClass().getName().startsWith(DEFAULT_HANDLER_PACKAGE_NAME)) {
                        Grove.e(TAG, "You already have an UncaughtExceptionHandler. If you use a custom UncaughtExceptionHandler, it should be initialized after UCEHandler! Installing anyway, but your original handler will not be called.");
                    }
                    application = (Application) context.getApplicationContext();
                    //Setup UCE Handler.
                    Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                        @Override
                        public void uncaughtException(Thread thread, final Throwable throwable) {
                            if (isUCEHEnabled) {
                                Grove.e(TAG, "App crashed, executing UCEHandler's UncaughtExceptionHandler", throwable);
                                if (hasCrashedInTheLastSeconds(application)) {
                                    Grove.e(TAG, "App already crashed recently, not starting custom error activity because we could enter a restart loop. Are you sure that your app does not crash directly on init?", throwable);
                                    if (oldHandler != null) {
                                        oldHandler.uncaughtException(thread, throwable);
                                        return;
                                    }
                                } else {
                                    setLastCrashTimestamp(application, new Date().getTime());
                                    if (!isInBackground || isBackgroundMode) {
                                        StringWriter sw = new StringWriter();
                                        PrintWriter pw = new PrintWriter(sw);
                                        throwable.printStackTrace(pw);
                                        String stackTraceString = sw.toString();
                                        if (stackTraceString.length() > MAX_STACK_TRACE_SIZE) {
                                            String disclaimer = " [stack trace too large]";
                                            stackTraceString = stackTraceString.substring(0, MAX_STACK_TRACE_SIZE - disclaimer.length()) + disclaimer;
                                        }
                                        if (isTrackActivitiesEnabled) {
                                            StringBuilder activityLogStringBuilder = new StringBuilder();
                                            while (!activityLog.isEmpty()) {
                                                activityLogStringBuilder.append(activityLog.poll());
                                            }
                                        }
//                                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                        List<Breadcrumb> breadcrumbs = Sentry.getContext().getBreadcrumbs();
                                        String logs = "";
                                        for (Breadcrumb breadcrumb : breadcrumbs) {
                                            logs += breadcrumb.getMessage() + "\t"
                                                    + breadcrumb.getTimestamp().toString()
                                                    + "\n";
                                        }
                                        new EmailHandler.Builder(context).setStackTrace(stackTraceString).setThrowable(throwable)
                                                .setSendingUserEmail(senderEmail)
                                                .setReceivingUserEmail(receiverEmail)
                                                .setActivityLog(activityLog).setAppLogs(logs).build()
                                                .sendErrorLogMail();
                                        errorHandler.onUncaughtExceptionReceived(context, stackTraceString, throwable.toString(), activityLog.toString(), logs);
                                        //application.startActivity(intent);
                                    } else {
                                        if (oldHandler != null) {
                                            oldHandler.uncaughtException(thread, throwable);
                                            return;
                                        }
                                        //If it is null (should not be), we let it continue and kill the process or it will be stuck
                                    }
                                }
                                final Activity lastActivity = lastActivityCreated.get();
                                if (lastActivity != null) {
                                    lastActivity.finish();
                                    lastActivityCreated.clear();
                                }
                                killCurrentProcess();
                            } else if (oldHandler != null) {
                                //Pass control to old uncaught exception handler
                                oldHandler.uncaughtException(thread, throwable);
                            }
                        }
                    });
                    application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                        final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                        int currentlyStartedActivities = 0;

                        @Override
                        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                            if (isTrackActivitiesEnabled) {
                                activityLog.add(dateFormat.format(new Date()) + ": " + activity.getClass().getSimpleName() + " created\n");
                            }
                        }

                        @Override
                        public void onActivityStarted(Activity activity) {
                            currentlyStartedActivities++;
                            isInBackground = (currentlyStartedActivities == 0);
                        }

                        @Override
                        public void onActivityResumed(Activity activity) {
                            if (isTrackActivitiesEnabled) {
                                activityLog.add(dateFormat.format(new Date()) + ": " + activity.getClass().getSimpleName() + " resumed\n");
                            }
                        }

                        @Override
                        public void onActivityPaused(Activity activity) {
                            if (isTrackActivitiesEnabled) {
                                activityLog.add(dateFormat.format(new Date()) + ": " + activity.getClass().getSimpleName() + " paused\n");
                            }
                        }

                        @Override
                        public void onActivityStopped(Activity activity) {
                            currentlyStartedActivities--;
                            isInBackground = (currentlyStartedActivities == 0);
                        }

                        @Override
                        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
                        }

                        @Override
                        public void onActivityDestroyed(Activity activity) {
                            if (isTrackActivitiesEnabled) {
                                activityLog.add(dateFormat.format(new Date()) + ": " + activity.getClass().getSimpleName() + " destroyed\n");
                            }
                        }
                    });
                }
                Log.i(TAG, "UCEHandler has been installed.");
            } else {
                Log.e(TAG, "Context can not be null");
            }
        } catch (Throwable throwable) {
            Log.e(TAG, "UCEHandler can not be initialized. Help making it better by reporting this as a bug.", throwable);
        }
    }

    /**
     * INTERNAL method that tells if the app has crashed in the last seconds.
     * This is used to avoid restart loops.
     *
     * @return true if the app has crashed in the last seconds, false otherwise.
     */
    private static boolean hasCrashedInTheLastSeconds(Context context) {
        long lastTimestamp = getLastCrashTimestamp(context);
        long currentTimestamp = new Date().getTime();
        return (lastTimestamp <= currentTimestamp && currentTimestamp - lastTimestamp < 3000);
    }

    @SuppressLint("ApplySharedPref")
    private static void setLastCrashTimestamp(Context context, long timestamp) {
        context.getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE).edit().putLong(SHARED_PREFERENCES_FIELD_TIMESTAMP, timestamp).commit();
    }

    private static void killCurrentProcess() {
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(10);
    }

    private static long getLastCrashTimestamp(Context context) {
        return context.getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE).getLong(SHARED_PREFERENCES_FIELD_TIMESTAMP, -1);
    }

    static void closeApplication(Activity activity) {
        activity.finish();
        killCurrentProcess();
    }

    public static class Builder {
        private Context context;
        private ErrorActivityHandler errorActivityHandler;
        private boolean isUCEHEnabled = true;
        private String commaSeparatedEmailAddresses;
        private boolean isTrackActivitiesEnabled = false;
        private boolean isBackgroundModeEnabled = true;
        private Application applicationInstance;
        private String receivingUserEmailId;
        private String sendingUserEmailId;

        public Builder(Context context) {
            this.context = context;
        }

        public Builder(Application application) {
            this.applicationInstance = application;
        }

        public Builder setUCEHEnabled(boolean isUCEHEnabled) {
            this.isUCEHEnabled = isUCEHEnabled;
            return this;
        }

        public Builder setErrorActivityHandler(ErrorActivityHandler errorActivityHandler) {
            this.errorActivityHandler = errorActivityHandler;
            return this;
        }

        public Builder setTrackActivitiesEnabled(boolean isTrackActivitiesEnabled) {
            this.isTrackActivitiesEnabled = isTrackActivitiesEnabled;
            return this;
        }

        public Builder setBackgroundModeEnabled(boolean isBackgroundModeEnabled) {
            this.isBackgroundModeEnabled = isBackgroundModeEnabled;
            return this;
        }

        public Builder addCommaSeparatedEmailAddresses(String commaSeparatedEmailAddresses) {
            this.commaSeparatedEmailAddresses = (commaSeparatedEmailAddresses != null) ? commaSeparatedEmailAddresses : "";
            return this;
        }

        public UCEHandler build() {
            return new UCEHandler(this);
        }

        public Builder setSendingUserEmailId(String sendingUserEmailId) {
            this.sendingUserEmailId = sendingUserEmailId;
            return this;
        }

        public Builder setReceivingUserEmailId(String receivingUserEmailId) {
            this.receivingUserEmailId = receivingUserEmailId;
            return this;
        }
    }
}