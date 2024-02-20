package cn.hikyson.android.godeye.xcrash;

import android.content.Context;
import android.content.pm.PackageManager;
import android.text.TextUtils;

import androidx.annotation.Keep;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import cn.hikyson.godeye.core.GodEye;
import cn.hikyson.godeye.core.internal.modules.crash.CrashConfig;
import cn.hikyson.godeye.core.internal.modules.crash.CrashInfo;
import cn.hikyson.godeye.core.utils.L;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import xcrash.ICrashCallback;
import xcrash.TombstoneManager;
import xcrash.TombstoneParser;
import xcrash.XCrash;


/*

https://zhuanlan.zhihu.com/p/52270464
那么我们该怎么调试引发 Crash 的 NDK 程序呢？
哈哈，好在 Google 早就料到了我们写的 NDK 代码肯定会漏洞百出。首先，当 NDK 程序在发生 Crash 时，
它会在路径 /data/tombstones/ 下产生导致程序 Crash 的文件 tombstone_xx。并且 Google 还在 NDK 包中为我们提供了一系列的调试工具，
例如 addr2line、objdump、ndk-stack。


Tombstone
Linux 信号机制
在介绍 Tombstone 之前，我们首先补充一个 Linux 信号机制的知识。

信号机制是 Linux 进程间通信的一种重要方式，Linux 信号一方面用于正常的进程间通信和同步，如任务控制(SIGINT, SIGTSTP,SIGKILL, SIGCONT，……)；另一方面，它还负责监控系统异常及中断。 当应用程序运行异常时， Linux 内核将产生错误信号并通知当前进程。 当前进程在接收到该错误信号后，可以有三种不同的处理方式。

1、忽略该信号。
2、捕捉该信号并执行对应的信号处理函数(signal handler)。
3、执行该信号的缺省操作(如 SIGSEGV， 其缺省操作是终止进程)。
当 Linux 应用程序在执行时发生严重错误，一般会导致程序 crash。其中，Linux 专门提供了一类 crash 信号，在程序接收到此类信号时，缺省操作是将 crash 的现场信息记录到 core 文件，然后终止进程。 */
@Keep
public class GodEyePluginXCrash {

    //https://github.com/iqiyi/xCrash/blob/master/README.zh-CN.md xCrach:anr,java,native crash

    /**
     * entrace
     *
     * @param crashContext
     * @param consumer
     */
    public static void init(CrashConfig crashContext, Consumer<List<CrashInfo>> consumer) {
        ICrashCallback callback = (logPath, emergency) -> {
            try {
                sendThenDeleteCrashLog(logPath, emergency, crashContext, consumer);
            } catch (IOException e) {
                L.e(e);
            }
        };
        XCrash.init(GodEye.instance().getApplication(), new XCrash.InitParameters()
                .setAppVersion(getAppVersion(GodEye.instance().getApplication()))
                .setJavaRethrow(true)
                .setJavaLogCountMax(10)
                .setJavaDumpAllThreadsWhiteList(new String[]{"^main$", "^Binder:.*", ".*Finalizer.*"})
                .setJavaDumpAllThreadsCountMax(10)
                .setJavaCallback(callback)
                .setNativeRethrow(true)
                .setNativeLogCountMax(10)
                .setNativeDumpAllThreadsWhiteList(new String[]{"^xcrash\\.sample$", "^Signal Catcher$", "^Jit thread pool$", ".*(R|r)ender.*", ".*Chrome.*"})
                .setNativeDumpAllThreadsCountMax(10)
                .setNativeCallback(callback)
                .setAnrRethrow(true)
                .setAnrLogCountMax(10)
                .setAnrCallback(callback)
                .setPlaceholderCountMax(3)
                .setPlaceholderSizeKb(512)
                .setLogFileMaintainDelayMs(1000));
        Schedulers.computation().scheduleDirect(() -> {
            try {
                sendThenDeleteCrashLogs(consumer);
            } catch (Exception e) {
                L.e(e);
            }
        });
    }

    private static void sendThenDeleteCrashLog(String logPath, String emergency, CrashConfig crashContext, Consumer<List<CrashInfo>> consumer) throws Exception {
        if (emergency != null || crashContext.immediate()) {// if emergency or immediate,output right now
            L.d("Crash produce message when emergency or immediate, crash count:%s, emergency:%s, logPath:%s", 1, emergency, logPath);
            consumer.accept(Collections.singletonList(wrapCrashMessage(TombstoneParser.parse(logPath, emergency))));
            TombstoneManager.deleteTombstone(logPath);
        }
    }

    private static void sendThenDeleteCrashLogs(Consumer<List<CrashInfo>> consumer) throws Exception {
        File[] files = TombstoneManager.getAllTombstones();
        List<CrashInfo> crashes = new ArrayList<>();
        for (File f : files) {
            try {
                crashes.add(wrapCrashMessage(TombstoneParser.parse(f.getAbsolutePath(), null)));
            } catch (IOException e) {
                L.e(e);
            }
        }
        if (!crashes.isEmpty()) {
            L.d("Crash produce message when install, crash count:%s", crashes.size());
            consumer.accept(crashes);
            TombstoneManager.clearAllTombstones();
        }
    }

    private static String getAppVersion(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            L.e(e);
        }
        return "";
    }

    private static CrashInfo wrapCrashMessage(Map<String, String> crashMap) {
        CrashInfo crashInfo = new CrashInfo();
        crashInfo.startTime = crashMap.remove(TombstoneParser.keyStartTime);
        crashInfo.crashTime = crashMap.remove(TombstoneParser.keyCrashTime);
        crashInfo.crashType = crashMap.remove(TombstoneParser.keyCrashType);
        crashInfo.processId = crashMap.remove(TombstoneParser.keyProcessId);
        crashInfo.processName = crashMap.remove(TombstoneParser.keyProcessName);
        crashInfo.threadId = crashMap.remove(TombstoneParser.keyThreadId);
        crashInfo.threadName = crashMap.remove(TombstoneParser.keyThreadName);
        crashInfo.nativeCrashCode = crashMap.remove(TombstoneParser.keyCode);
        crashInfo.nativeCrashSignal = crashMap.remove(TombstoneParser.keySignal);
        crashInfo.nativeCrashBacktrace = crashMap.remove(TombstoneParser.keyBacktrace);
        crashInfo.nativeCrashStack = crashMap.remove(TombstoneParser.keyStack);
        crashInfo.javaCrashStacktrace = crashMap.remove(TombstoneParser.keyJavaStacktrace);
        crashInfo.extras = crashMap;
        StringBuilder sb = new StringBuilder();
        if (!TextUtils.isEmpty(crashInfo.nativeCrashCode)) {
            sb.append(crashInfo.nativeCrashCode).append(" | ");
        }
        if (!TextUtils.isEmpty(crashInfo.nativeCrashSignal)) {
            sb.append(crashInfo.nativeCrashSignal).append(" | ");
        }
        String javaStackTrace = crashInfo.javaCrashStacktrace;
        if (javaStackTrace != null) {
            String[] javaStackTraceLines = javaStackTrace.split("\n");
            if (javaStackTraceLines.length > 0) {
                sb.append(javaStackTraceLines[0]);
            }
        }
        crashInfo.crashMessage = String.valueOf(sb);
        return crashInfo;
    }
}
