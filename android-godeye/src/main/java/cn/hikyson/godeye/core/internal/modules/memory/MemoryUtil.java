package cn.hikyson.godeye.core.internal.modules.memory;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Debug;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

import cn.hikyson.godeye.core.utils.ProcessUtils;

/**
 * Created by kysonchao on 2017/11/22.
 *
 * 内存耗用：VSS/RSS/PSS/USS 的介绍
 * https://www.jianshu.com/p/3bab26d25d2e
 *
 * PSS - Proportional Set[集] Size (共享比例内存) （仅供参考）
 * 实际使用的物理内存（比例分配共享库占用的内存，按照进程数等比例划分）
 *
 * USS - Unique Set Size （非常有用）
 * 进程独自占用的物理内存（不包含共享库占用的内存）。USS是非常非常有用的数据，因为它反映了运行一个特定进程真实的边际成本（增量成本）。
 */


/**
 * 指标不同：Runtime.getRuntime().totalMemory() 返回的是 Java 虚拟机（JVM）当前分配的总内存量，而 DalvikPss 则是 Android 系统中用于度量进程内存使用的指标之一。
 * pss= dalvikPss + nativePss + otherPss.
 * Runtime.getRuntime().maxMemory() = Runtime.getRuntime().freeMemory()+usedMemory
 */
public class MemoryUtil {
    private static AtomicLong sTotalMem = new AtomicLong(0L);
    private static ActivityManager sActivityManager;

    /**
     * 获取应用dalvik内存信息
     * 耗时忽略不计
     *
     * @return dalvik堆内存KB
     */
    public static HeapInfo getAppHeapInfo() {
        Runtime runtime = Runtime.getRuntime();
        HeapInfo heapInfo = new HeapInfo();
        heapInfo.freeMemKb = runtime.freeMemory() / 1024;
        heapInfo.maxMemKb = Runtime.getRuntime().maxMemory() / 1024;
        heapInfo.allocatedKb = (Runtime.getRuntime().totalMemory() - runtime.freeMemory()) / 1024;
        return heapInfo;
    }

//    /**
//     * get native heap
//     *
//     * @return
//     */
//    public static NativeHeapInfo getAppNativeHeap() {
//        NativeHeapInfo nativeHeapInfo = new NativeHeapInfo();
//
//        nativeHeapInfo.heapSizeKb = Debug.getNativeHeapSize() / 1024;
//        nativeHeapInfo.heapAllocatedKb = Debug.getNativeHeapAllocatedSize() / 1024;
//        nativeHeapInfo.heapFreeSizeKb = Debug.getNativeHeapFreeSize() / 1024;
//        return nativeHeapInfo;
//    }

    /**
     * 获取应用实际占用RAM
     *
     * @param context
     * @return 应用pss信息KB
     */
    public static PssInfo getAppPssInfo(Context context) {
        final int pid = ProcessUtils.getCurrentPid();
        if (sActivityManager == null) {
            sActivityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        }
        Debug.MemoryInfo memoryInfo = sActivityManager.getProcessMemoryInfo(new int[]{pid})[0];
        PssInfo pssInfo = new PssInfo();
        pssInfo.totalPssKb = memoryInfo.getTotalPss();
        pssInfo.dalvikPssKb = memoryInfo.dalvikPss;
        pssInfo.nativePssKb = memoryInfo.nativePss;
        pssInfo.otherPssKb = memoryInfo.otherPss;
        return pssInfo;
    }

    public static RamInfo getRamInfo(Context context) {
        if (sActivityManager == null) {
            sActivityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        }
        final ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        sActivityManager.getMemoryInfo(mi);
        final RamInfo ramMemoryInfo = new RamInfo();
        ramMemoryInfo.availMemKb = mi.availMem / 1024;
        ramMemoryInfo.isLowMemory = mi.lowMemory;
        ramMemoryInfo.lowMemThresholdKb = mi.threshold / 1024;
        ramMemoryInfo.totalMemKb = getRamTotalMem(sActivityManager);
        return ramMemoryInfo;
    }

    /**
     * 同步获取系统的总ram大小
     *
     * @param activityManager
     * @return
     */
    private static long getRamTotalMem(ActivityManager activityManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            activityManager.getMemoryInfo(mi);
            return mi.totalMem / 1024;
        } else if (sTotalMem.get() > 0L) {//如果已经从文件获取过值，则不需要再次获取
            return sTotalMem.get();
        } else {
            final long tm = getRamTotalMemByFile();
            sTotalMem.set(tm);
            return tm;
        }
    }


    /**
     * 获取手机的RAM容量，其实和activityManager.getMemoryInfo(mi).totalMem效果一样，也就是说，在API16以上使用系统API获取，低版本采用这个文件读取方式
     *
     * @return 容量KB
     */
    private static long getRamTotalMemByFile() {
        final String dir = "/proc/meminfo";
        try {
            FileReader fr = new FileReader(dir);
            BufferedReader br = new BufferedReader(fr, 2048);
            String memoryLine = br.readLine();
            String subMemoryLine = memoryLine.substring(memoryLine
                    .indexOf("MemTotal:"));
            br.close();
            return (long) Integer.parseInt(subMemoryLine.replaceAll(
                    "\\D+", ""));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0L;
    }
}
