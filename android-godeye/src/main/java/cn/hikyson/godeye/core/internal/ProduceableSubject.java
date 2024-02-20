package cn.hikyson.godeye.core.internal;

import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;

/**
 * Created by kysonchao on 2017/11/23.
 *
 * 发生状态改变的对象被称为“主题”(Subject)，依赖它的对象被称为“观察者”(Observer）
 */
public class ProduceableSubject<T> implements SubjectSupport<T>, Producer<T> {
    private Subject<T> mSubject;

    public ProduceableSubject() {
        mSubject = createSubject();
    }

    protected Subject<T> createSubject() {
        return PublishSubject.create();
    }

    @Override
    public void produce(T data) { //生产事件用于被消费
        mSubject.onNext(data); //发射数据，Consumer
    }

    @Override
    public Observable<T> subject() {
        return mSubject;
    }
}
