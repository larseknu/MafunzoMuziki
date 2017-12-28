package no.hiof.larseknu.mafonzomuziki.util;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

/**
 * Created by larseknu on 22.12.2017.
 */

public class NotificationUtil {

    public static void createNotificationChannel(Context context) {
        if(Build.VERSION.SDK_INT>=26) {
            NotificationChannel channel = new NotificationChannel("MafunzoMuzikiMusic", "MafunzoMuzikiMusic", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setSound(null, null);
            channel.setDescription("Main Music Channel");
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(channel);
        }
    }
}
