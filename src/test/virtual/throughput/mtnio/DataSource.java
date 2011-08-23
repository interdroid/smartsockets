/**
 *
 */
package test.virtual.throughput.mtnio;

class DataSource {

    private int count = 0;
    private boolean done = false;

    synchronized void set(int count) {
        this.count = count;
        notifyAll();
    }

    synchronized void done() {
        done = true;
        notifyAll();
    }

    synchronized boolean waitForStartOrDone() {
        while (!done && count == 0) {
            try {
                wait();
            } catch (Exception e) {
                // ignore
            }
        }

        return done;
    }

    synchronized int getBlock() {

        if (count == 0) {
            return -1;
        }

        count--;

        if (count == 0) {
            notifyAll();
        }

        return count;
    }
}
