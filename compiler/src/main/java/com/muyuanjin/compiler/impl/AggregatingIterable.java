package com.muyuanjin.compiler.impl;

import lombok.NonNull;

import java.util.Iterator;

record AggregatingIterable<T>(Iterable<T> left, Iterable<T> right) implements Iterable<T> {
    @NonNull
    @Override
    public Iterator<T> iterator() {
        return new Iterator<>() {
            private final Iterator<T> i1 = left == null ? null : left.iterator();
            private final Iterator<T> i2 = right == null ? null : right.iterator();
            private boolean iteratingFirst = i1 != null && i1.hasNext();

            public boolean hasNext() {
                return iteratingFirst ? i1.hasNext() : i2 != null && i2.hasNext();
            }

            public T next() {
                if (iteratingFirst) {
                    T next = i1.next();
                    iteratingFirst = i1.hasNext();
                    return next;
                }
                return i2.next();
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }
}
