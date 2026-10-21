/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.quarkmind.inference.bayes.bitmask;

public class LongBitMask implements BitMask {

    private long mask;

    public LongBitMask() {}

    public LongBitMask(long mask) {
        this.mask = mask;
    }

    @Override
    public BitMask set(int index) {
        if (index >= 64) {
            return BitMask.getEmpty(index + 1).setAll(this).set(index);
        }
        this.mask = this.mask | (1L << index);
        return this;
    }

    @Override
    public BitMask setAll(BitMask mask) {
        if (mask instanceof LongBitMask) {
            this.mask |= ((LongBitMask) mask).asLong();
        } else if (mask instanceof OpenBitSet) {
            return mask.setAll(this);
        }
        return this;
    }

    @Override
    public BitMask reset(int index) {
        if (index < 64) {
            mask = mask & (Long.MAX_VALUE - (1L << index));
        }
        return this;
    }

    @Override
    public BitMask resetAll(BitMask mask) {
        if (mask instanceof LongBitMask) {
            this.mask &= (-1L - ((LongBitMask) mask).asLong());
        }
        return this;
    }

    @Override
    public boolean isSet(int index) {
        long bit = 1L << index;
        return (mask & bit) == bit;
    }

    @Override
    public boolean isAllSet() {
        return mask == -1L;
    }

    @Override
    public boolean isEmpty() {
        return mask == 0;
    }

    @Override
    public boolean intersects(BitMask mask) {
        return mask instanceof LongBitMask ?
               (this.mask & ((LongBitMask) mask).asLong()) != 0 :
               mask.intersects(this);
    }

    public long asLong() {
        return mask;
    }

    @Override
    public LongBitMask clone() {
        return new LongBitMask(mask);
    }

    @Override
    public String getInstancingStatement() {
        return "new " + LongBitMask.class.getCanonicalName() + "(" + mask + "L)";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {return true;}
        return o instanceof LongBitMask && mask == ((LongBitMask) o).mask;
    }

    @Override
    public int hashCode() {
        return (int) (mask ^ (mask >>> 32));
    }

    @Override
    public String toString() {
        return "" + mask;
    }
}
