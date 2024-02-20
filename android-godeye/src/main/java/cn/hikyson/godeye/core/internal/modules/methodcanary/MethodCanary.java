package cn.hikyson.godeye.core.internal.modules.methodcanary;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import cn.hikyson.godeye.core.GodEye;
import cn.hikyson.godeye.core.internal.Install;
import cn.hikyson.godeye.core.internal.ProduceableSubject;
import cn.hikyson.godeye.core.utils.JsonUtil;
import cn.hikyson.godeye.core.utils.L;
import cn.hikyson.methodcanary.lib.MethodEvent;
import cn.hikyson.methodcanary.lib.ThreadInfo;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.Subject;
import okio.BufferedSink;
import okio.Okio;

//扩展1：
//Debug.startMethodTracing() 是 Android SDK 提供的一个方法，用于启动方法追踪（Method Tracing）。其原理是在应用程序运行时，记录下每个方法的调用时间和执行时间，以便后续分析应用程序的性能瓶颈。
//具体原理如下：
//启动追踪：调用 Debug.startMethodTracing() 方法后，系统开始记录应用程序的方法调用信息。
//记录方法调用：当应用程序运行时，每次调用一个方法时，系统都会记录下该方法的调用时间、执行时间等信息，并存储在一个文件中。
//停止追踪：在某个指定的时间后，调用 Debug.stopMethodTracing() 方法停止追踪。停止追踪后，系统将保存追踪数据到一个文件中。
//分析追踪数据：生成的追踪文件可以通过 Android Studio 的 Profiler 或者其他性能分析工具进行分析。通过分析追踪文件，可以找出应用程序中耗时较长的方法，从而优化应用程序的性能。
//需要注意的是，启用方法追踪可能会对应用程序的性能产生一定的影响，因为系统需要额外的开销来记录方法调用信息。因此，在生产环境中应该谨慎使用，避免影响用户体验。通常情况下，方法追踪主要用于开发和调试阶段，用来定位和解决性能问题。


//扩展2
//https://robolectric.org/  Robolectric 是一个 Android 单元测试框架，它可以在 JVM 上运行 Android 应用程序，而不需要连接到实际的设备或模拟器。Robolectric 可以模拟 Android 框架的行为，包括 Activity 生命周期、Fragment 生命周期、View 的绘制和事件处理等。使用 Robolectric 可以大大提高 Android 单元测试的效率，因为它可以在本地机器上快速运行测试，而不需要连接到设备或模拟器。
//Robolectric基本原理+Shadow类阴影的概念
//在使用Robolectric之前我们先要明白Robolectric是如何工作的。比如说我们前文说到的TextView，如果我们使用Mockito，他给我们提供的是Mock后的TextView，而Robolectric给我们提供的是ShadowTextView，这个ShadowTextView实现了TextView身上的方法，但他又与Android的运行环境无关，也就是说他可以像使用TextView一样的方法，但不用在平台上运行代码，大大提高测试效率。

//为什么要用Robolectric？
//要测试Android代码逻辑，光有JUnit和Mockito是不够的，假设你使用了TextView的setText,用Mockito框架的话，默认的TextView的getText方法会返回null,如果是简单的代码，使用Mockito的桩设置还可以接受，如果是要测试到Activity的生命周期等一些复杂逻辑就显得比较复杂了。
//为了解决这个问题,诞生了Instrumentation[android内置的]、Robolectric等等的测试框架，不过Instrumentation实际上还是要运行代码到平台上测试，耗费大量的时间，我们今天要介绍的是运行在JVM上的Robolectric测试框架。
// 如：Instrumentation
// defaultConfig {
//         applicationId 'com.example.kotlintest'
//         minSdk 23
//         targetSdk 33
//         versionCode 1
//         versionName "1.0"
//
//         testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner" //注意这里！！！！！
// }

//应用了作者自己的库MethodCanary(插桩（in/out）+方法计时统计)
public class MethodCanary extends ProduceableSubject<MethodsRecordInfo> implements Install<MethodCanaryConfig> {
    private boolean mInstalled = false;
    private MethodCanaryConfig mMethodCanaryContext;

    @Override
    public synchronized boolean install(final MethodCanaryConfig methodCanaryContext) {
        if (this.mInstalled) {
            L.d("MethodCanary already installed, ignore.");
            return true;
        }
        this.mMethodCanaryContext = methodCanaryContext;
        this.mInstalled = true;
        L.d("MethodCanary installed.");
        return true;
    }

    @Override
    public synchronized void uninstall() {
        if (!this.mInstalled) {
            L.d("MethodCanary already uninstalled, ignore.");
            return;
        }
        this.mMethodCanaryContext = null;
        this.mInstalled = false;
        L.d("MethodCanary uninstalled.");
    }

    @Override
    public synchronized boolean isInstalled() {
        return this.mInstalled;
    }

    @Override
    public MethodCanaryConfig config() {
        return mMethodCanaryContext;
    }

    public synchronized void startMonitor(String tag) {
        try {
            if (!isInstalled()) {
                L.d("MethodCanary start monitor fail, not installed.");
                return;
            }
            cn.hikyson.methodcanary.lib.MethodCanary.get().startMethodTracing(tag);
            L.d("MethodCanary start monitor success.");
        } catch (Exception e) {
            L.d("MethodCanary start monitor fail:" + e);
        }
    }

    public synchronized void stopMonitor(String tag) {
        try {
            if (!isInstalled()) {
                L.d("MethodCanary stop monitor fail, not installed.");
                return;
            }
            cn.hikyson.methodcanary.lib.MethodCanary.get().stopMethodTracing(tag
                    , new cn.hikyson.methodcanary.lib.MethodCanaryConfig(this.mMethodCanaryContext.lowCostMethodThresholdMillis()), (sessionTag, startMillis, stopMillis, methodEventMap) -> {
                        long start0 = System.currentTimeMillis();
                        MethodsRecordInfo methodsRecordInfo = MethodCanaryConverter.convertToMethodsRecordInfo(startMillis, stopMillis, methodEventMap);
//                        recordToFile(methodEventMap, methodsRecordInfo);
                        long start1 = System.currentTimeMillis();
                        MethodCanaryConverter.filter(methodsRecordInfo, this.mMethodCanaryContext);
                        long end = System.currentTimeMillis();
                        L.d(String.format("MethodCanary output success! cost %s ms, filter cost %s ms", end - start0, end - start1));
                        produce(methodsRecordInfo);
                    });
            L.d("MethodCanary stopped monitor and output processing...");
        } catch (Exception e) {
            L.d("MethodCanary stop monitor fail:" + e);
        }
    }

    private static void recordToFile(Map<ThreadInfo, List<MethodEvent>> methodEventMap, MethodsRecordInfo methodsRecordInfo) {
        for (File file : GodEye.instance().getApplication().getExternalCacheDir().listFiles()) {
            file.delete();
        }
        File file = new File(GodEye.instance().getApplication().getExternalCacheDir(), "methodcanary_methodEventMap.txt");
        try {
            BufferedSink buffer = Okio.buffer(Okio.sink(file));
            buffer.writeUtf8(JsonUtil.toJson(methodEventMap)).flush();
            buffer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        File file2 = new File(GodEye.instance().getApplication().getExternalCacheDir(), "methodcanary_methodsRecordInfo.txt");
        try {
            BufferedSink buffer = Okio.buffer(Okio.sink(file2));
            buffer.writeUtf8(JsonUtil.toJson(methodsRecordInfo)).flush();
            buffer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public synchronized boolean isRunning(String tag) {
        return cn.hikyson.methodcanary.lib.MethodCanary.get().isMethodTraceRunning(tag);
    }

    @Override
    protected Subject<MethodsRecordInfo> createSubject() {
        return BehaviorSubject.create();
    }
}
