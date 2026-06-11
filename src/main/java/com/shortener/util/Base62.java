package com.shortener.util;

public class Base62 {
    private static final String CHARACTERS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = CHARACTERS.length();

    /**
     * Encodes a positive 62-bit integer into a Base62 string.
     */
    public static String encode(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Base62 encoding only supports non-negative numbers");
        }
        if (value == 0) {
            return "0";
        }
        
        StringBuilder sb = new StringBuilder();
        long temp = value;
        while (temp > 0) {
            sb.append(CHARACTERS.charAt((int) (temp % BASE)));
            temp /= BASE;
        }
        return sb.reverse().toString();
    }

    /**
     * Decodes a Base62 string back into a 62-bit integer.
     */
    public static long decode(String str) {
        if (str == null || str.isEmpty()) {
            throw new IllegalArgumentException("Base62 string cannot be null or empty");
        }
        
        long value = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            int index = CHARACTERS.indexOf(c);
            if (index == -1) {
                throw new IllegalArgumentException("Invalid Base62 character: " + c);
            }
            value = value * BASE + index;
        }
        return value;
    }
}
