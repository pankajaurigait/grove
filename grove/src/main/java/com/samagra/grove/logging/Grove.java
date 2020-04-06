package com.samagra.grove.logging;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import com.samagra.grove.BuildConfig;
import com.samagra.grove.hyperlog.HyperLog;

import io.sentry.Sentry;
import io.sentry.android.AndroidSentryClientFactory;
import io.sentry.event.BreadcrumbBuilder;
import timber.log.Timber;

public class Grove {
    private static LoggableApplication applicationInstance;
    private static String TAG = BuildConfig.FLAVOR.toUpperCase();
    private static String appName = "N/A";

    public static void init(LoggableApplication applicationInstance, Context context, boolean isHyperlogEnabled, String domainID) {
        Grove.applicationInstance = applicationInstance;
        HyperLog.initialize(context, isHyperlogEnabled);
        HyperLog.setLogLevel(Log.VERBOSE);
        initSentry(context, domainID);
    }


    private static void initSentry(Context context, String domainID) {
        Sentry.init(domainID, new AndroidSentryClientFactory(context));
        Sentry.getContext().recordBreadcrumb(new BreadcrumbBuilder().setMessage("User made an action").build());
    }

    private static Class initClass() {
        if(applicationInstance == null) return null;
        Activity currentActivity = applicationInstance.getCurrentActivity();
        Class clazz = applicationInstance.getClass();

        if (currentActivity != null)
         {
            TAG = currentActivity.getClass().getName();
            clazz = currentActivity.getClass();
        }

        return clazz;
    }

    public static void e(String message) {
        Class clazz = initClass();
        Timber.tag(clazz.getName());
        Timber.e(message);
        HyperLog.e(clazz.getName(),message);
        Sentry.record(new BreadcrumbBuilder().setMessage(clazz.getName() + "::" + message).build());
    }

    public static void e(Throwable t, String message, Object... args) {
        Timber.e(t, message, args);
        StringBuilder stringBuilder = new StringBuilder("");
        for(Object argument : args){
            stringBuilder.append("" + argument.toString() + "");
        }
        HyperLog.e(TAG,"Error with Arguments\n" + stringBuilder.toString() + " " +message, t);    }

    public static void e(String message, Object... args) {
        Class clazz = initClass();
        Timber.e(message, args);
        StringBuilder stringBuilder = new StringBuilder("");
        for(Object argument : args){
            stringBuilder.append("" + argument.toString() + "");
        }
        HyperLog.e(clazz.getName(),"Error with Arguments\n" + stringBuilder.toString() + " " +message);
        Sentry.record(new BreadcrumbBuilder().setMessage(clazz.getName() + "::" + formatMessage(message, args)).build());
    }

    public static void e(Throwable e) {
        Class clazz = initClass();
        Timber.tag(clazz.getName());
        Timber.e(e);
        HyperLog.e(clazz.getName(),"", e);
        Sentry.capture(e);
    }

    public static void e(String TAG, String message) {
        Class clazz = initClass();
        Timber.tag(TAG);
        Timber.e(message);
        HyperLog.e(TAG,message);
        Sentry.record(new BreadcrumbBuilder().setMessage(TAG + "::" + message).build());
    }

    public static void d(Throwable e) {
        Class clazz = initClass();
        Timber.tag(clazz.getName());
        Timber.d(e);
        HyperLog.d(clazz.getName(),"", e);
        Sentry.capture(e);
    }

    public static void d(String message) {
        Class clazz = initClass();
        Timber.tag(clazz.getName());
        Timber.d(message);
        HyperLog.d(clazz.getName(),message);
        Sentry.record(new BreadcrumbBuilder().setMessage(clazz.getName() + "::" + message).build());
    }

    public static void i(Throwable e) {
        Class clazz = initClass();
        Timber.tag(clazz.getName());
        Timber.i(e);
        HyperLog.i(clazz.getName(),"", e);
        Sentry.capture(e);
    }

    public static void d(Throwable t, String message, Object... args) {
        Class clazz = initClass();
        Timber.d(t, message, args);
        StringBuilder stringBuilder = new StringBuilder("");
        for(Object argument : args){
            stringBuilder.append("" + argument.toString() + "");
        }
        HyperLog.d(clazz.getName(),"Debug Log with Arguments\n" + stringBuilder.toString() + " " +message, t);
        Sentry.record(new BreadcrumbBuilder().setMessage(clazz.getName() + "::" + formatMessage(message, args)).build());
        Sentry.capture(t);
    }

    public static void d(String message, Object... args) {
        Class clazz = initClass();
        Timber.i(message, args);
        StringBuilder stringBuilder = new StringBuilder("");
        for(Object argument : args){
            stringBuilder.append("" + argument.toString() + "");
        }
        HyperLog.d(clazz.getName(),"Warning with Arguments\n" + stringBuilder.toString() + " " +message);
        Sentry.record(new BreadcrumbBuilder().setMessage(clazz.getName() + "::" + formatMessage(message, args)).build());
    }

    public static void i(String message) {
        Class clazz = initClass();
        if(clazz != null) Timber.tag(clazz.getName());
        Timber.i(message);
        HyperLog.i(clazz.getName(),message);
    }

    public static void i(Throwable t, String message, Object... args) {
        Class clazz = initClass();
        Timber.i(t, message, args);
        StringBuilder stringBuilder = new StringBuilder("");
        for(Object argument : args){
            stringBuilder.append("" + argument.toString() + "");
        }
        HyperLog.i(clazz.getName(),"Info Log with Arguments\n" + stringBuilder.toString() + " " +message, t);
        Sentry.record(new BreadcrumbBuilder().setMessage(clazz.getName() + "::" + formatMessage(message, args)).build());
        Sentry.capture(t);
    }

    public static void i(String message, Object... args) {
        Class clazz = initClass();
        Timber.i(message, args);
        StringBuilder stringBuilder = new StringBuilder("");
        for(Object argument : args){
            stringBuilder.append("" + argument.toString() + "");
        }
        HyperLog.i(clazz.getName(),"Info Log with Arguments\n" + stringBuilder.toString() + " " +message);
    }

    public static void v(String message) {
        Class clazz = initClass();
        Timber.tag(clazz.getName());
        Timber.v(message);
        HyperLog.v(clazz.getName(),message);

    }

    public static void v(Throwable t, String message, Object... args) {
        Class clazz = initClass();
        Timber.v(t, message, args);
        HyperLog.v(clazz.getName(),message);
        Sentry.record(new BreadcrumbBuilder().setMessage(clazz.getName() + "::" + formatMessage(message, args)).build());
        Sentry.capture(t);
    }

    public static void v(String message, Object... args) {
        Class clazz = initClass();
        Timber.v(message, args);
        HyperLog.v(clazz.getName(),message);

    }

    public static void w(String message) {
        Class clazz = initClass();
        Timber.tag(clazz.getName());
        Timber.w(message);
        HyperLog.w(clazz.getName(),message);
    }

    public static void w(Throwable t, String message, Object... args) {
        Class clazz = initClass();
        Timber.w(t, message, args);
        StringBuilder stringBuilder = new StringBuilder("");
        for(Object argument : args){
            stringBuilder.append("" + argument.toString() + "");
        }
        HyperLog.d(clazz.getName(),"Warning with Arguments\n" + stringBuilder.toString() + " " +message, t);
        Sentry.record(new BreadcrumbBuilder().setMessage(clazz.getName() + "::" + formatMessage(message, args)).build());
        Sentry.capture(t);
    }

    public static void w(String message, Object... args) {
        Class clazz = initClass();
        Timber.w(message, args);
        StringBuilder stringBuilder = new StringBuilder("");
        for(Object argument : args){
            stringBuilder.append("" + argument.toString() + "");
        }
        HyperLog.w(clazz.getName(),"Warning with Arguments\n" + stringBuilder.toString() + " " +message);
        Sentry.record(new BreadcrumbBuilder().setMessage(clazz.getName() + "::" + formatMessage(message, args)).build());
    }

    public static void w(Throwable e) {
        Class clazz = initClass();
        Timber.tag(clazz.getName());
        Timber.w(e);
        HyperLog.w(clazz.getName(),"",e);

        Sentry.capture(e);
    }

    /**
     * Formats a log message with optional arguments.
     */
    public static String formatMessage(String message, Object[] args) {
        return String.format(message, args);
    }

}