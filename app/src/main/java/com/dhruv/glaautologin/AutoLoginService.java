package com.dhruv.glaautologin;
import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;
import java.io.IOException;
import java.util.regex.*;
import okhttp3.*;

public class AutoLoginService extends Service {
    private ConnectivityManager.NetworkCallback networkCallback;
    private final OkHttpClient client = new OkHttpClient();
    private static final String PORTAL_URL = "https://captive.onlinegla.com/";

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("AutoLoginChannel", "Auto Login Service", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
        Notification.Builder builder = new Notification.Builder(this, "AutoLoginChannel")
                .setContentTitle("Auto Login Active")
                .setContentText("Monitoring network state...")
                .setSmallIcon(android.R.drawable.ic_menu_info_details);
        startForeground(1, builder.build());
        registerNetworkCallback();
    }

    private void registerNetworkCallback() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkRequest request = new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build();
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) {
                super.onAvailable(network);
                authenticate();
            }
        };
        cm.registerNetworkCallback(request, networkCallback);
    }

    private void authenticate() {
        client.newCall(new Request.Builder().url(PORTAL_URL).build()).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) { }
            @Override public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    Matcher matcher = Pattern.compile("name=\"magic\" value=\"([^\"]+)\"").matcher(response.body().string());
                    String magicToken = matcher.find() ? matcher.group(1) : "";
                    
                    FormBody.Builder form = new FormBody.Builder()
                            .add("username", "2415000542")
                            .add("password", "UPDATE_ME");
                    if (!magicToken.isEmpty()) form.add("magic", magicToken);

                    client.newCall(new Request.Builder().url(PORTAL_URL).post(form.build()).build()).enqueue(new Callback() {
                        @Override public void onFailure(Call call, IOException e) { }
                        @Override public void onResponse(Call call, Response response) throws IOException { response.close(); }
                    });
                }
                response.close();
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (networkCallback != null) {
            ((ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE)).unregisterNetworkCallback(networkCallback);
        }
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
