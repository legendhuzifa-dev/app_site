package com.example.p2pmessenger;

import android.app.Application;
import android.content.Context;

public class PertoApp extends Application {
    private static volatile Context appContext;

    @Override public void onCreate() {
        super.onCreate();
        appContext = getApplicationContext();
        
        // অ্যাপ চালুর সাথে সাথে গিটহাব থেকে নতুন সার্ভার লিংক আপডেট করবে
        ServerConfig.fetchLatestUrlAsync();
    }

    public static Context context() { return appContext; }
}
