/*
 * Copyright 2015 Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.onosproject.store.service;

/**
 * Top level exception for Transaction failures.
 */
@SuppressWarnings("serial")
public class TransactionException extends RuntimeException {
    public TransactionException() {
    }

    public TransactionException(Throwable t) {
        super(t);
    }

    /**
     * Transaction timeout.
     */
    public static class Timeout extends TransactionException {
    }

    /**
     * Transaction interrupted.
     */
    public static class Interrupted extends TransactionException {
    }

    /**
     * Transaction failure due to optimistic concurrency failure.
     */
    public static class OptimisticConcurrencyFailure extends TransactionException {
    }
}