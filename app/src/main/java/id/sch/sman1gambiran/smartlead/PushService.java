package id.sch.sman1gambiran.smartlead;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/** Menerima notifikasi dari server SMART LEAD (Firebase Cloud Messaging) dan menampilkannya di bilah notifikasi HP. */
public class PushService extends FirebaseMessagingService {
    public static final String CHANNEL = "smartlead";

    static void siapkanChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || nm.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "SMART LEAD", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Izin guru, pengingat mengajar, jam kosong, ringkasan harian");
        nm.createNotificationChannel(ch);
    }

    @Override
    public void onMessageReceived(RemoteMessage m) {
        Map<String, String> d = m.getData();
        String judul = d.get("judul"), isi = d.get("isi");
        if (judul == null && m.getNotification() != null) { judul = m.getNotification().getTitle(); isi = m.getNotification().getBody(); }
        if (judul == null) return;
        tampil(this, judul, isi == null ? "" : isi, d.get("halaman"));
    }

    static void tampil(Context c, String judul, String isi, String halaman) {
        siapkanChannel(c);
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        Intent i = new Intent(c, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (halaman != null && !halaman.isEmpty()) i.putExtra("halaman", halaman);
        int id = (int) (System.currentTimeMillis() & 0x7fffffff);
        PendingIntent pi = PendingIntent.getActivity(c, id, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_notif)
                .setColor(0xFF0D6B5C)
                .setContentTitle(judul)
                .setContentText(isi)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(isi))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi);
        try { NotificationManagerCompat.from(c).notify(id, b.build()); } catch (SecurityException ignored) { }
    }

    @Override
    public void onNewToken(String token) {
        getSharedPreferences("sl", MODE_PRIVATE).edit().putString("fcm", token).apply();
    }
}
