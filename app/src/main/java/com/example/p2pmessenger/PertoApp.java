package com.example.p2pmessenger;

import android.app.Application;
import android.content.Context;

/** Keeps an application Context so ApiClient/Session work from any thread or service. */
public class PertoApp extends Application {
    private static volatile Context appContext;

    @Override public void onCreate() {
        super.onCreate();
        appContext = getApplicationContext();
    }

    public static Context context() { return appContext; }
}
