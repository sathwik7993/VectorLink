package com.shortener.util;

public class FeistelCipher {
    private static final int ROUNDS = 4;
    
    // Four cryptographically random 32-bit odd keys to serve as round keys
    private static final int[] KEYS = {
        0x4f35a1a9, 
        0x1b9e28c4, 
        0x7c3a510e, 
        0x3d9f2e8b
    };
    
    // Mask to constrain values to 31 bits (giving a 62-bit range when L and R are joined)
    private static final int MASK = 0x7FFFFFFF; 

    /**
     * Obfuscates a positive number up to 2^62 - 1.
     * Guaranteed to be collision-free and reversible.
     */
    public static long encrypt(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Feistel cipher only supports non-negative numbers");
        }
        
        int left = (int) ((value >>> 31) & MASK);
        int right = (int) (value & MASK);

        for (int i = 0; i < ROUNDS; i++) {
            int temp = (left ^ roundFunction(right, KEYS[i])) & MASK;
            left = right;
            right = temp;
        }

        return ((long) left << 31) | right;
    }

    /**
     * Reverses the obfuscation of a positive 62-bit number.
     */
    public static long decrypt(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Feistel cipher only supports non-negative numbers");
        }
        
        int left = (int) ((value >>> 31) & MASK);
        int right = (int) (value & MASK);

        for (int i = ROUNDS - 1; i >= 0; i--) {
            int temp = (right ^ roundFunction(left, KEYS[i])) & MASK;
            right = left;
            left = temp;
        }

        return ((long) left << 31) | right;
    }

    /**
     * Non-linear mixing function for Feistel round.
     */
    private static int roundFunction(int val, int roundKey) {
        long hash = ((long) val * 0xdeece66dL) + roundKey;
        hash = (hash ^ (hash >>> 16)) * 0x27bb2ee687b0b0fdL;
        return (int) ((hash ^ (hash >>> 32)) & MASK);
    }
}
