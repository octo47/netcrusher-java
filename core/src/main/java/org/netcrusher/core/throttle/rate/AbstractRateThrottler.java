package org.netcrusher.core.throttle.rate;

import org.netcrusher.core.throttle.Throttler;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

public abstract class AbstractRateThrottler implements Throttler {

    private final long periodNs;

    private final long rate;

    private long markerNs;

    private int count;

    protected AbstractRateThrottler(long rate, long rateTime, TimeUnit rateTimeUnit) {
        if (rate < 1 || rate > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Rate value is invalid");
        }

        if (rateTimeUnit.toHours(rateTime) > 1) {
            throw new IllegalArgumentException("Period is too high");
        }
        if (rateTimeUnit.toMillis(rateTime) < 10) {
            throw new IllegalArgumentException("Period is too small");
        }

        this.periodNs = rateTimeUnit.toNanos(rateTime);
        this.rate = rate;

        this.markerNs = nowNs();
        this.count = 0;
    }

    @Override
    public long calculateDelayNs(InetSocketAddress clientAddress, ByteBuffer bb) {
        final long nowNs = nowNs();
        final long elapsedNs = nowNs - markerNs; // could be even negative

        count += events(clientAddress, bb);

        if (elapsedNs > periodNs || count > rate) {
            final double registered = count;
            final double allowed = 1.0 * rate * elapsedNs / periodNs;
            if (registered > allowed) {
                final double excess = registered - allowed;
                final long pauseNs = Math.round(periodNs * excess / rate);
                if (pauseNs > 0) {
                    markerNs = nowNs + pauseNs;
                    count = 0;

                    return pauseNs;
                }
            }
        }

        return NO_DELAY;
    }

    protected long nowNs() {
        return System.nanoTime();
    }

    protected abstract int events(InetSocketAddress clientAddress, ByteBuffer bb);

}