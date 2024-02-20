package cn.hikyson.godeye.core.internal.modules.sm.core;


import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 线程堆栈信息dump
 */
public class StackSampler extends AbstractSampler {

    private static final int DEFAULT_MAX_ENTRY_COUNT = 30;
    private static final LinkedHashMap<Long, StackTraceElement[]> sStackMap = new LinkedHashMap<>();

    private int mMaxEntryCount;
    private Thread mCurrentThread;

    StackSampler(Thread thread, long sampleIntervalMillis, long sampleDelay) {
        this(thread, DEFAULT_MAX_ENTRY_COUNT, sampleIntervalMillis, sampleDelay);
    }

    StackSampler(Thread thread, int maxEntryCount, long sampleIntervalMillis, long sampleDelay) {
        super(sampleIntervalMillis, sampleDelay);
        mCurrentThread = thread;
        mMaxEntryCount = maxEntryCount;
    }

    /**
     * 获取这个时间段内dump的堆栈信息
     *
     * @param startTime
     * @param endTime
     * @return
     */
    Map<Long, List<StackTraceElement>> getThreadStackEntries(long startTime, long endTime) {
        Map<Long, List<StackTraceElement>> result = new LinkedHashMap<>();
        synchronized (sStackMap) {
            for (Long entryTime : sStackMap.keySet()) {
                if (startTime < entryTime && entryTime < endTime) {
                    result.put(entryTime, Arrays.asList(sStackMap.get(entryTime)));
                }
            }
        }
        return result;
    }

    @Override
    protected void doSample() {
        synchronized (sStackMap) {
            if (sStackMap.size() == mMaxEntryCount && mMaxEntryCount > 0) {
                sStackMap.remove(sStackMap.keySet().iterator().next());
            }
            sStackMap.put(System.currentTimeMillis(), mCurrentThread.getStackTrace());
        }

        //StackTraceElement[]是一个完整的调用栈。每一个元素都是一个方法。其中包含了方法名、文件名、行号等信息。 如下面的例子
        //https://www.cnblogs.com/jonzone/p/5501197.html
        //-------getStackTrace : java.lang.Thread.getStackTrace(Unknown Source)
        //-------methodC : org.feinno.icm.cms.Test.methodC(Test.java:24)
        //-------methodB : org.feinno.icm.cms.Test.methodB(Test.java:20)
        //-------methodA : org.feinno.icm.cms.Test.methodA(Test.java:16)
        //-------OuterMethod : org.feinno.icm.cms.TestM.OuterMethod(Test.java:33)
        //-------main : org.feinno.icm.cms.Test.main(Test.java:11)
    }
}