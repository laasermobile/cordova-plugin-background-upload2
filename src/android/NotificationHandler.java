package com.spoon.backgroundfileupload;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.res.Resources;
import android.widget.RemoteViews;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import net.gotev.uploadservice.UploadService;
import net.gotev.uploadservice.data.UploadInfo;
import net.gotev.uploadservice.data.UploadNotificationConfig;
import net.gotev.uploadservice.data.UploadRate;
import net.gotev.uploadservice.observer.task.AbstractSingleNotificationHandler;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class NotificationHandler extends AbstractSingleNotificationHandler {

    private Activity mContext;
    private long uploadCount = 0;
    private float speed = 0;
    private int inProgress = 0;
    private String defaultTitle;
    private String defaultContent;
    private PendingIntent mPendingIntent;
    private ManagerService mService;

    public NotificationHandler(@NotNull UploadService service, ManagerService managerService, Activity context, PendingIntent pendingIntent, String defaultTitle, String defaultContent) {
        super(service);
        this.mService = managerService;
        this.mContext = context;
        this.mPendingIntent = pendingIntent;
        this.defaultTitle = defaultTitle;
        this.defaultContent = defaultContent;
    }

    @Override
    public void onStart(@NotNull UploadInfo info, int notificationId, @NotNull UploadNotificationConfig notificationConfig) {
        super.onStart(info, notificationId, notificationConfig);
        this.uploadCount = PendingUpload.count(PendingUpload.class);
    }

    @Override
    public void onCompleted(@NotNull UploadInfo info, int notificationId, @NotNull UploadNotificationConfig notificationConfig) {
        super.onCompleted(info, notificationId, notificationConfig);
        removeTask(info.getUploadId());
        this.uploadCount = PendingUpload.count(PendingUpload.class);
    }

    private RemoteViews refresh() {
        String pkg = mContext.getApplication().getPackageName();
        String layoutDef = "layout";
        String idDef = "id";

        Resources resources = mContext.getResources();

        RemoteViews notificationLayout = new RemoteViews(mContext.getPackageName(),
                resources.getIdentifier("notification_small", layoutDef, pkg));

        String title;
        String leftContent;
        String rightContent = "";

        if (mService.isNetworkAvailable) {
            title = inProgress == 0 ? defaultTitle : String.format("%s upload(s) remaining", uploadCount);
            leftContent = inProgress == 0 ? defaultContent : String.format("%d in progress", inProgress);
            rightContent = inProgress == 0 ? "" : toReadable(speed);
        } else {
            title = defaultTitle;
            leftContent = "Waiting for connection";
        }

        notificationLayout.setTextViewText(resources.getIdentifier("notification_title", idDef, pkg), title);
        notificationLayout.setTextViewText(resources.getIdentifier("notification_content_left", idDef, pkg), leftContent);
        notificationLayout.setTextViewText(resources.getIdentifier("notification_content_right", idDef, pkg), rightContent);

        return notificationLayout;
    }

    @Nullable
    @Override
    public NotificationCompat.Builder updateNotification(@NotNull NotificationManager notificationManager, @NotNull NotificationCompat.Builder builder, @NotNull Map<String, TaskData> tasks) {
        this.speed = 0;
        this.inProgress = 0;

        for (Map.Entry<String, TaskData> entry : tasks.entrySet()) {
            if (entry.getValue().getStatus() == TaskStatus.InProgress) {
                inProgress++;
                speed += convertUnitToKbps(
                        entry.getValue().getInfo().getUploadRate().getUnit().name(),
                        entry.getValue().getInfo().getUploadRate().getValue()
                );
            }
        }

        RemoteViews notificationLayout = refresh();

        return builder
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(notificationLayout)
                .setSmallIcon(android.R.drawable.ic_menu_upload)
                .setContentIntent(mPendingIntent);
    }

    private float convertUnitToKbps(String unit, int speed) {
        final String BPS = UploadRate.UploadRateUnit.BitPerSecond.name();
        final String KPS = UploadRate.UploadRateUnit.KilobitPerSecond.name();
        final String MPS = UploadRate.UploadRateUnit.MegabitPerSecond.name();

        float value = 0;
        if (unit == BPS) {
            value = speed / 8000f;
        }
        if (unit == KPS) {
            value = speed / 8;
        }
        if (unit == MPS) {
            value = speed * 125;
        }

        return value;
    }

    private String toReadable(float speed) {
        final String BPS = "B/s";
        final String KBPS = "kB/s";
        final String MBPS = "MB/s";

        if (speed >= 1000) {
            return String.format("%.0f %s", speed / 1000, MBPS);
        }
        if (speed < 1) {
            return String.format("%.0f %s", speed * 1000, BPS);
        }

        return String.format("%.0f %s", speed, KBPS);
    }
}
