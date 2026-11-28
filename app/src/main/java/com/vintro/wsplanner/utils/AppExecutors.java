package com.vintro.wsplanner.utils;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

// shared bounded executors and main thread handler for background work
public final class AppExecutors {
    private static final int IO_THREAD_COUNT = 4;
    private static final AtomicInteger threadCounter = new AtomicInteger(1);

    private static final ExecutorService IO_EXECUTOR = Executors.newFixedThreadPool(IO_THREAD_COUNT, runnable -> {
        Thread thread = new Thread(runnable, "wsplanner-io-" + threadCounter.getAndIncrement());
        thread.setDaemon(true);
        return thread;
    });

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private AppExecutors() {
    }

    public static ExecutorService io() {
        return IO_EXECUTOR;
    }

    public static void runIo(Runnable task) {
        if (task == null) {
            return;
        }
        IO_EXECUTOR.execute(task);
    }

    public static void runOnMain(Runnable task) {
        if (task == null) {
            return;
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            task.run();
        } else {
            MAIN_HANDLER.post(task);
        }
    }

    public static Handler mainHandler() {
        return MAIN_HANDLER;
    }
}
