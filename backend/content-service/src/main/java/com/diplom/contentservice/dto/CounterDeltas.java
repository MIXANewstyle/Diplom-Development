package com.diplom.contentservice.dto;

public record CounterDeltas(long upvotes, long comments, long views) {
    public static CounterDeltas zero() {
        return new CounterDeltas(0, 0, 0);
    }
}
