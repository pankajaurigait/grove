package com.samagra.grove.logging;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClient;
import com.amazonaws.services.simpleemail.model.Body;
import com.amazonaws.services.simpleemail.model.Content;
import com.amazonaws.services.simpleemail.model.Destination;
import com.amazonaws.services.simpleemail.model.Message;
import com.amazonaws.services.simpleemail.model.SendEmailRequest;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Deque;
import java.util.Locale;

class EmailHandler {
    private final static int SUCCESS = 0;
    private final static int ERROR = 1;
    private Deque<String> activityLogs;
    private final Throwable mThrowable;
    private int result;
    private String strCurrentErrorLog;
    private Context context;
    private String stackTrace;
    private String mLogs;
    private String senderMailID;
    private String receiverMailID;


    private EmailHandler(EmailHandler.Builder builder) {
        context = builder.context;
        stackTrace = builder.mStackTrace;
        activityLogs = builder.mActivityLog;
        mLogs = builder.mLogs;
        mThrowable = builder.mThrowable;
        senderMailID = builder.mSendingUserEmail;
        receiverMailID = builder.mReceivingUserEmail;
    }

    void sendErrorLogMail() {
        // TODO: REPLACE WITH YOUR IDENTITY POOL AND REGION and LOADS CREDENTIALS FROM AWS COGNITO IDENTITY POOL
        CognitoCachingCredentialsProvider credentials = new CognitoCachingCredentialsProvider(
                context,
                "us-west-2:213e041d-1a39-4006-90eb-e7fa28d6a41b", // IDENTITY POOL ID
                Regions.US_WEST_2 // REGION
        );

        // CREATES SES CLIENT TO MANAGE SENDING EMAIL
        final AmazonSimpleEmailServiceClient ses = new AmazonSimpleEmailServiceClient(credentials);
        ses.setRegion(Region.getRegion(Regions.US_WEST_2));

        Content subject = new Content(generateMailSubject());

        Body body = new Body(new Content(getAllErrorDetailsFromIntent()));
        final Message message = new Message(subject, body);
        final String from = senderMailID;
        String to = receiverMailID;

        final Destination destination = new Destination()
                .withToAddresses(to.contentEquals("") ? null : Arrays.asList(to.split("\\s*,\\s*")));

        // CREATES SEPARATE THREAD TO ATTEMPT TO SEND EMAIL
        Thread sendEmailThread = new Thread(new Runnable() {
            public void run() {
                try {
                    SendEmailRequest request = new SendEmailRequest(from, destination, message);
                    ses.sendEmail(request);
                    result = SUCCESS;
                } catch (Exception e) {
                    result = ERROR;
                    Grove.e("Crash report could not be sent to the back-end Server.", e);
                }
            }
        });

        // RUNS SEND EMAIL THREAD
        sendEmailThread.start();

        try {
            // WAITS THREAD TO COMPLETE TO ACT ON RESULT
            sendEmailThread.join();
            if (result == SUCCESS) {
                Grove.d("Crash report has been successfully sent to the back-end Server.");
            }
        } catch (InterruptedException e) {
            Grove.e("Crash report could not be sent to the back-end Server.", e);
            e.printStackTrace();
        }
    }

    private String generateMailSubject() {
        String[] lines = stackTrace.split(":");
        return "[CTT]  "+getApplicationName(context).toUpperCase() + " User: " + GroveUtils.getUserName() + " - ver(" + getVersionName(context) + ") - " +lines[0].substring(10)+ ":" +lines[1];

    }

    private String getApplicationName(Context context) {
        ApplicationInfo applicationInfo = context.getApplicationInfo();
        int stringId = applicationInfo.labelRes;
        return stringId == 0 ? applicationInfo.nonLocalizedLabel.toString() : context.getString(stringId);
    }

    private String getVersionName(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionName;
        } catch (Exception e) {
            return "Unknown";
        }
    }
    private String getAllErrorDetailsFromIntent() {
        if (TextUtils.isEmpty(strCurrentErrorLog)) {
            String LINE_SEPARATOR = "\n";
            String userName = GroveUtils.getUserName();
            StringBuilder errorReport = new StringBuilder();
            errorReport.append("\n***** Error Title \n");
            errorReport.append(getApplicationName(context));
            errorReport.append(LINE_SEPARATOR);
            errorReport.append("Error: ");
            String[] lines = stackTrace.split(":");
            if(lines[0].contains("java.lang."))
                errorReport.append(lines[0].substring(10));
            String versionName = getVersionName(context);
            errorReport.append(lines[1]);
            String line[] = lines[3].split("at");
            errorReport.append(LINE_SEPARATOR);
            errorReport.append(line[0]);
            errorReport.append(LINE_SEPARATOR);

            errorReport.append("\n***** BreadCrumbs \n");
            errorReport.append(mLogs);

            errorReport.append("\n***** USER INFO \n");
            errorReport.append("Name: ");
            errorReport.append(userName);
            errorReport.append(LINE_SEPARATOR);
            errorReport.append("User Data: ");
            errorReport.append(GroveUtils.getUserData());
            errorReport.append(LINE_SEPARATOR);
            errorReport.append(GroveUtils.getUserData());
            errorReport.append(LINE_SEPARATOR);

            errorReport.append("\n***** DEVICE INFO \n");
            errorReport.append("Brand: ");
            errorReport.append(Build.BRAND);
            errorReport.append(LINE_SEPARATOR);
            errorReport.append("Device: ");
            errorReport.append(Build.DEVICE);
            errorReport.append(LINE_SEPARATOR);
            errorReport.append("Model: ");
            errorReport.append(Build.MODEL);
            errorReport.append(LINE_SEPARATOR);
            errorReport.append("Manufacturer: ");
            errorReport.append(Build.MANUFACTURER);
            errorReport.append(LINE_SEPARATOR);
            errorReport.append("Product: ");
            errorReport.append(Build.PRODUCT);
            errorReport.append(LINE_SEPARATOR);
            errorReport.append("SDK: ");
            errorReport.append(Build.VERSION.SDK);
            errorReport.append(LINE_SEPARATOR);
            errorReport.append("Release: ");
            errorReport.append(Build.VERSION.RELEASE);
            errorReport.append(LINE_SEPARATOR);

            errorReport.append("\n***** APP INFO \n");
            errorReport.append("Version: ");
            errorReport.append(versionName);
            errorReport.append(LINE_SEPARATOR);
            Date currentDate = new Date();
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            String firstInstallTime = getFirstInstallTimeAsString(context, dateFormat);
            if (!TextUtils.isEmpty(firstInstallTime)) {
                errorReport.append("Installed On: ");
                errorReport.append(firstInstallTime);
                errorReport.append(LINE_SEPARATOR);
            }
            String lastUpdateTime = getLastUpdateTimeAsString(context, dateFormat);
            if (!TextUtils.isEmpty(lastUpdateTime)) {
                errorReport.append("Updated On: ");
                errorReport.append(lastUpdateTime);
                errorReport.append(LINE_SEPARATOR);
            }
            errorReport.append("Current Date: ");
            errorReport.append(dateFormat.format(currentDate));
            errorReport.append(LINE_SEPARATOR);
            errorReport.append("\n***** ERROR LOG \n");
            errorReport.append(stackTrace);
            errorReport.append(LINE_SEPARATOR);
            String activityLog = activityLogs.toString();
            errorReport.append(mThrowable);
            errorReport.append(LINE_SEPARATOR);
            if (activityLog != null) {
                errorReport.append("\n***** USER ACTIVITIES \n");
                errorReport.append("User Activities: ");
                errorReport.append(activityLog);
                errorReport.append(LINE_SEPARATOR);
            }
            errorReport.append("\n***** END OF LOG *****\n");
            strCurrentErrorLog = errorReport.toString();
            return strCurrentErrorLog;
        } else {
            return strCurrentErrorLog;
        }
    }


    private String getFirstInstallTimeAsString(Context context, DateFormat dateFormat) {
        long firstInstallTime;
        try {
            firstInstallTime = context
                    .getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0)
                    .firstInstallTime;
            return dateFormat.format(new Date(firstInstallTime));
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }


    private String getLastUpdateTimeAsString(Context context, DateFormat dateFormat) {
        long lastUpdateTime;
        try {
            lastUpdateTime = context
                    .getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0)
                    .lastUpdateTime;
            return dateFormat.format(new Date(lastUpdateTime));
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    static class Builder {
        private Context context;
        private String mStackTrace;
        private Throwable mThrowable;
        private Deque<String> mActivityLog;
        private String mLogs;
        private String mSendingUserEmail;
        private String mReceivingUserEmail;

        Builder(Context context) {
            this.context = context;
        }

        EmailHandler.Builder setStackTrace(String stackTrace) {
            this.mStackTrace = stackTrace;
            return this;
        }

        EmailHandler build() {
            return new EmailHandler(this);
        }

        EmailHandler.Builder setThrowable(Throwable throwable) {
            this.mThrowable = throwable;
            return this;
        }

        EmailHandler.Builder setActivityLog(Deque<String> activityLog) {
            this.mActivityLog = activityLog;
            return this;
        }

        EmailHandler.Builder setAppLogs(String logs) {
            this.mLogs = logs;
            return this;
        }

        public Builder setSendingUserEmail(String sendingUserEmail) {
            this.mSendingUserEmail = sendingUserEmail;
            return this;
        }

        public Builder setReceivingUserEmail(String receivingUserEmail) {
            this.mReceivingUserEmail = receivingUserEmail;
            return this;
        }
    }
}