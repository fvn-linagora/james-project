/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.modules.server;

import java.util.function.Function;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

public class DefaultRetryer<R> implements Retryer<R> {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder<R> {
        private int maxAttemptsNumber = 1;
        private long waitDelayInMillis = 0;
        private Class<? extends Throwable> exceptionClass;

        public Builder whileLessAttemptsThan(int maxAttemptsNumber) {
            this.maxAttemptsNumber = maxAttemptsNumber;
            return this;
        }

        public Builder waitingBetweenAttempts(long waitDelayInMillis) {
            this.waitDelayInMillis = waitDelayInMillis;
            return this;
        }

        public Builder retryIfExceptionOfType(Class<? extends Throwable> exceptionClass) {
            this.exceptionClass = exceptionClass;
            return this;
        }

        public Retryer<R> build() {
            Preconditions.checkState(exceptionClass != null, "'retryIfExceptionOfType' is required");
            return new DefaultRetryer<R>(maxAttemptsNumber, waitDelayInMillis, exceptionClass);
        }
    }

    private final int maxAttemptsNumber;
    private final long waitDelayInMillis;
    private final Class<? extends Throwable> exceptionClass;

    @VisibleForTesting
    DefaultRetryer(int maxAttemptsNumber, long waitDelayInMillis, Class<? extends Throwable> exceptionClass) {
        this.maxAttemptsNumber = maxAttemptsNumber;
        this.waitDelayInMillis = waitDelayInMillis;
        this.exceptionClass = exceptionClass;
    }

    @Override
    public <T> R retry(Function<T, R> provider, T input) {
        return retry(exceptionClass, provider, input, maxAttemptsNumber, waitDelayInMillis);
    }

    private static <E extends Throwable, T, R> R retry(Class<? extends Throwable> exceptionType, Function<T, R> provider, T input, int maxRetries, long delayMillis) {
        int retryCounter = 0;
        boolean hasSucceeded = false;
        R result = null;
        while(retryCounter < maxRetries && !hasSucceeded) {
            try {
                result = provider.apply(input);
                hasSucceeded = true;
            } catch (Exception e) {
                if (exceptionType.isInstance(e)) {
                    retryCounter++;
                    try {
                        Thread.sleep(delayMillis);
                    } catch (InterruptedException e1) {
                        throw Throwables.propagate(e1);
                    }
                }
            }
        }
        return result;
    }

}
