/**
 *
 */
package test.virtual.throughput.mt;

class DataSink {

    private int count = 0;

    public synchronized void waitForCount(int count) {

        while (this.count < count) {
            try {
                wait();
            } catch (Exception e) {
                // ignore
            }
        }

        this.count -= count;
    }

    public synchronized void done() {
        count++;
        notifyAll();
    }
}
