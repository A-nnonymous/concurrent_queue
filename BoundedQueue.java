package queue;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.System.currentTimeMillis;
// 任务：把Bounded Blocking Lock-based Queue中的size变量替换为enqSize和deqSize两个变量，给出实现代码并进行性能评估。

/**
 * Bounded blocking queue
 */
public class BoundedQueue<T> {
    private static int counter = 0;
    // Lock out other enqueuers (dequeuers)
    ReentrantLock enqLock, deqLock;
    /**
     * wait/signal when queue is not empty or not full
     */
    Condition notEmptyCondition, notFullCondition;
    /**
     * Number of empty slots.
     */
    AtomicInteger size;
    AtomicInteger deqSize;
    AtomicInteger enqSize;
    /**
     * First entry in queue.
     */
    Entry head;
    /**
     * Last entry in queue.
     */
    Entry tail;
    /**
     * Max number of entries allowed in queue.
     */
    int capacity;

    /**
     * Constructor.
     *
     * @param capacity Max number of items allowed in queue.
     */
    public BoundedQueue(int capacity) {
        this.capacity = capacity;
        this.head = new Entry(null);
        this.tail = head;
        this.size = new AtomicInteger(capacity);

        // my code
        this.enqSize = new AtomicInteger(0);
        this.deqSize = new AtomicInteger(0);
        // my code

        this.enqLock = new ReentrantLock();
        this.notFullCondition = enqLock.newCondition();
        this.deqLock = new ReentrantLock();
        this.notEmptyCondition = deqLock.newCondition();
        System.out.println("Time: [" + counter++ +
                "] Thread: [" +
                Thread.currentThread().getId() +
                "]: Created bounded queue with capacity [" +
                capacity + "]");

    }

    public static void main(String[] args) {
        int mission_num = 8000;
        int ThreadsNum ;
        int Mission_ThreadNum[] = {2,4,6,8};
        int repeat_time = 30;
        long duration_sum = 0;
        BoundedQueue<Long> Q = new BoundedQueue<Long>(mission_num);
        for (int tn:Mission_ThreadNum) {
            ThreadsNum = tn;
            for (int tNum = 1; tNum <= ThreadsNum; tNum++) {
                long start;
                for (int repeat = 0; repeat < repeat_time; repeat++) {
                    start = System.nanoTime();
                    for (int i = 0; i < ThreadsNum; i++) {
                        AddingRunnable r = new AddingRunnable(Q, i % 2 == 0, mission_num / ThreadsNum);//50 percent of each operation.
                        Thread t = new Thread(r);
                        t.start();
                        try {
                            t.join();
                        } catch (InterruptedException e) {
                        }
                    }
                    duration_sum += System.nanoTime() - start;
                    //System.out.println(currentTimeMillis()-start);
                }
            }
            double duration = (duration_sum / repeat_time)/1000;
            System.out.println("Using " + ThreadsNum + " Threads,\t" + "Time elapsed avg: " + duration+ " microsec");
        }
    }

    public int getCapacity() {
        return capacity;
    }

    /**
     * Remove and return head of queue.
     *
     * @return remove first item in queue
     */
    public T deq() {
        T result;
        boolean mustWakeEnqueuers = false;
        //System.out.println("Time: [" + counter++ + "] Thread: [" + Thread.currentThread().getId()+ "]: Acquired Lock for dequeing an element.");

        deqLock.lock();
        try {
            //while (size.get() == capacity) {
            /////////////////////////////////////
            while (deqSize.get()== enqSize.get()) {
            /////////////////////////////////////
                try {
                    //System.out.println("Time: [" + counter++ + "] Thread: [" + Thread.currentThread().getId() + "] waiting for notEmptyCondition.");
                    notEmptyCondition.await();
                } catch (InterruptedException ex) {
                }
            }
            result = head.next.value;
            head = head.next;
            //if (size.getAndIncrement() == 0) {
            /////////////////////////////////////
            if (enqSize.get() - deqSize.getAndIncrement() == capacity) {
            /////////////////////////////////////
                mustWakeEnqueuers = true;
            }
        } finally {
            deqLock.unlock();
        }
        if (mustWakeEnqueuers) {
            enqLock.lock();
            try {
                //System.out.println("Time: [" + counter++ + "] Thread: [" + Thread.currentThread().getId() + "] Signalling not full condition after a dequeue operation.");
                notFullCondition.signalAll();
            } finally {
                enqLock.unlock();
            }
        }
        return result;
    }

    /**
     * Append item to end of queue.
     *
     * @param x item to append
     * @return
     */
    public Object enq(T x) {
        if (x == null)
            throw new NullPointerException();
        boolean mustWakeDequeuers = false;
        //System.out.println("Time: [" + counter++ + "] Thread: [" + Thread.currentThread().getId() + "]: Acquired Lock for enqueing an element.");

        enqLock.lock();
        try {
            //while (size.get() == 0) {
            ///////////////////////////////////////////////////
            while (enqSize.get() - deqSize.get() == capacity) {
            ///////////////////////////////////////////////////
                try {
                    //System.out.println("Time: [" + counter++ + "] Thread: [" + Thread.currentThread().getId() + "] waiting for notFullCondition.");
                    notFullCondition.await();
                } catch (InterruptedException e) {
                }
            }
            Entry e = new Entry(x);
            tail.next = e;
            tail = e;
            //if (size.getAndDecrement() == capacity) {
            ///////////////////////////////////////////////////
            if (enqSize.getAndIncrement() - deqSize.get() == capacity) {
            ///////////////////////////////////////////////////
                mustWakeDequeuers = true;
            }
        } finally {
            enqLock.unlock();
        }
        if (mustWakeDequeuers) {
            deqLock.lock();
            try {
                //System.out.println("Time: [" + counter++ + "] Thread: [" + Thread.currentThread().getId() + "] Signalling not empty condition after a enqueue operation.");
                notEmptyCondition.signalAll();
            } finally {
                deqLock.unlock();
            }
        }
        return null;
    }

    /**
     * Individual queue item.
     */
    protected class Entry {
        /**
         * Actual value of queue item.
         */
        public T value;
        /**
         * next item in queue
         */
        public Entry next;

        /**
         * Constructor
         *
         * @param x Value of item.
         */
        public Entry(T x) {
            value = x;
            next = null;
        }
    }
}

class AddingRunnable implements Runnable {
    private final BoundedQueue<Long> Q;
    private final int operation_num;
    private final boolean operation;

    public AddingRunnable(BoundedQueue<Long> Q, boolean operation, int operation_num) {
        this.Q = Q;
        this.operation = operation;
        this.operation_num = operation_num;
    }

    public void run() {
        Thread t = Thread.currentThread();
        long element = t.getId();
        try {
            for (int i = 0; i < operation_num; i++) {
                if (this.operation == true) {
                    Q.enq(element);
                } else {
                    Q.deq();
                }
            }
        } finally {
            return;
        }
    }
}

