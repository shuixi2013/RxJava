/**
 * Copyright 2015 Netflix, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 * the License for the specific language governing permissions and limitations under the License.
 */

/*
 * Inspired by fastutils' OpenHashSet implementation at
 * https://github.com/vigna/fastutil/blob/master/drv/OpenHashSet.drv
 */

package io.reactivex.internal.util;

import java.util.Arrays;
import java.util.function.Consumer;

import io.reactivex.exceptions.CompositeException;

/**
 * A simple open hash set with add, remove and clear capabilities only.
 * <p>Doesn't support nor checks for {@code null}s.
 *
 * @param <T> the element type
 */
public final class OpenHashSet<T> {
    final float loadFactor;
    int mask;
    int size;
    int maxSize;
    T[] keys;
    
    public OpenHashSet() {
        this(16, 0.75f);
    }
    
    /**
     * Creates an OpenHashSet with the initial capacity and load factor of 0.75f.
     * @param capacity the initial capacity
     */
    public OpenHashSet(int capacity) {
        this(capacity, 0.75f);
    }
    
    @SuppressWarnings("unchecked")
    public OpenHashSet(int capacity, float loadFactor) {
        this.loadFactor = loadFactor;
        int c = Pow2.roundToPowerOfTwo(capacity);
        this.mask = c - 1;
        this.maxSize = (int)(loadFactor * c);
        this.keys = (T[])new Object[c];
    }
    
    public boolean add(T value) {
        final T[] a = keys;
        final int m = mask;
        
        int pos = mix(value.hashCode()) & m;
        T curr = a[pos];
        if (curr != null) {
            if (curr.equals(value)) {
                return false;
            }
            for (;;) {
                pos = (pos + 1) & m;
                curr = a[pos];
                if (curr == null) {
                    break;
                }
                if (curr.equals(value)) {
                    return false;
                }
            }
        }
        a[pos] = value;
        if (++size >= maxSize) {
            rehash();
        }
        return true;
    }
    public boolean remove(T value) {
        T[] a = keys;
        int m = mask;
        int pos = mix(value.hashCode()) & m;
        T curr = a[pos];
        if (curr == null) {
            return false;
        }
        if (curr.equals(value)) {
            return removeEntry(pos, a, m);
        }
        for (;;) {
            pos = (pos + 1) & m;
            curr = a[pos];
            if (curr == null) {
                return false;
            }
            if (curr.equals(value)) {
                return removeEntry(pos, a, m);
            }
        }
    }
    
    boolean removeEntry(int pos, T[] a, int m) {
        size--;
        
        int last;
        int slot;
        T curr;
        for (;;) {
            last = pos;
            pos = (pos + 1) & m;
            for (;;) {
                curr = a[pos];
                if (curr == null) {
                    a[last] = null;
                    return true;
                }
                slot = mix(curr.hashCode()) & m;
                
                if (last <= pos ? last >= slot || slot > pos : last >= slot && slot > pos) {
                    break;
                }
                
                pos = (pos + 1) & m;
            }
            a[last] = curr;
        }
    }
    
    public void clear(Consumer<? super T> clearAction) {
        if (size == 0) {
            return;
        }
        T[] a = keys;
        int len = a.length;
        for (int i = 0; i < len; i++) {
            T e = a[i];
            if (e != null) {
                clearAction.accept(e);
            }
        }
        Arrays.fill(a, null);
        size = 0;
    }
    
    @SuppressWarnings("unchecked")
    void rehash() {
        T[] a = keys;
        int i = a.length;
        int newCap = i << 1;
        int m = newCap - 1;
        
        T[] b = (T[])new Object[newCap];
        
        
        for (int j = size; j-- != 0; ) {
            while (a[--i] == null);
            int pos = mix(a[i].hashCode()) & m;
            if (b[pos] != null) {
                for (;;) {
                    pos = (pos + 1) & m;
                    if (b[pos] == null) {
                        break;
                    }
                }
            }
            b[pos] = a[i];
        }
        
        this.mask = m;
        this.maxSize = (int)(newCap * loadFactor);
        this.keys = b;
    }
    
    private static final int INT_PHI = 0x9E3779B9;
    
    static int mix(int x) {
        final int h = x * INT_PHI;
        return h ^ (h >>> 16);
    }
    
    public void forEach(Consumer<? super T> consumer) {
        for (T k : keys) {
            if (k != null) {
                consumer.accept(k);
            }
        }
    }

    /**
     * Loops through all values in the set and collects any exceptions from the consumer
     * into a Throwable.
     * @param consumer the consumer to call
     * @return if not null, contains a CompositeException with all the suppressed exceptions
     */
    public Throwable forEachSuppress(Consumer<? super T> consumer) {
        CompositeException ex = null;
        int count = 0;
        for (T k : keys) {
            if (k != null) {
                try {
                    consumer.accept(k);
                } catch (Throwable e) {
                    if (ex == null) {
                        ex = new CompositeException();
                    }
                    count++;
                    ex.addSuppressed(e);
                }
            }
        }
        if (count == 1) {
            return ex.getSuppressed()[0];
        }
        return ex;
    }
    
    public boolean isEmpty() {
        return size == 0;
    }
}
