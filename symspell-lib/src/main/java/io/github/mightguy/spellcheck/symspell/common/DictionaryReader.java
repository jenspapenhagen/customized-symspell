package io.github.mightguy.spellcheck.symspell.common;

import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Fast reader for {@code term<TAB or ;>frequency} dictionary files.
 *
 * <p>Uses buffered byte input and word-at-a-time scanning (SWAR) for line boundaries,
 * avoiding {@link java.nio.file.Files#readAllLines} and {@link String#split}. Only the
 * term bytes are decoded to a {@link String}; the frequency is parsed directly from the
 * byte buffer. A TAB is the preferred separator (term may contain spaces, e.g. bigrams);
 * a whitespace fallback keeps compatibility with legacy space-separated files. Malformed
 * lines are skipped silently.
 */
public final class DictionaryReader {
    private static final int LONG_ALIGNMENT_MASK = 7;
    private static final int TAB_BYTE = 0x09;
    private static final int LONG_SIZE_BITS = 3;
    private static final int ASCII_ZERO = 0x30;
    private static final int BYTE_MASK = 0xFF;
    private static final int DECIMAL_BASE = 10;
    private static final int DIGIT_MASK = 0x0F;
    private static final int LONG_BYTES = 8;
    private static final long LOW_BITS_ONE = 0x0101010101010101L;
    private static final long HIGH_BITS = 0x8080808080808080L;


    private static final int BUFFER_SIZE = 64 * 1024;

    private DictionaryReader() {
    }

    /**
     * Reads a dictionary file.
     *
     * @param path the dictionary file to read
     * @return the parsed entries (never {@code null})
     * @throws IOException if the file cannot be opened or read
     */
    public static List<DictionaryItem> read(final Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        try (InputStream input = Files.newInputStream(path)) {
            return read(input);
        }
    }

    /**
     * Reads a dictionary from an input stream.
     *
     * @param input the input stream to read from
     * @return the parsed entries (never {@code null})
     * @throws IOException if reading fails
     */
    public static List<DictionaryItem> read(final InputStream input) throws IOException {
        Objects.requireNonNull(input, "input");
        final List<DictionaryItem> result = new ArrayList<>();
        byte[] buffer = new byte[BUFFER_SIZE];
        int pending = 0;
        while (true) {
            if (pending == buffer.length) {
                buffer = Arrays.copyOf(buffer, Math.multiplyExact(buffer.length, 2));
            }
            final int read = input.read(buffer, pending, buffer.length - pending);
            final MemorySegment source = MemorySegment.ofArray(buffer);
            if (read < 0) {
                if (pending > 0) {
                    parseLine(source, 0, pending, result);
                }
                return result;
            }
            final int limit = pending + read;
            int start = 0;
            while (start < limit) {
                final long end = findEither(source, start, limit, (byte) '\n', (byte) '\r');
                if (end == limit) {
                    break;
                }
                parseLine(source, start, (int) end, result);
                start = (int) end + 1;
            }
            pending = limit - start;
            if (pending > 0) {
                System.arraycopy(buffer, start, buffer, 0, pending);
            }
        }
    }

    private static void parseLine(final MemorySegment source, final int start, final int endIn,
            final List<DictionaryItem> result) {
        int end = endIn;
        while (end > start && isWhitespace(source.get(ValueLayout.JAVA_BYTE, end - 1))) {
            end--;
        }
        if (end <= start) {
            return;
        }
        // prefer a TAB or ';' separator (term may contain spaces, e.g. bigrams).
        // Scan right-to-left: the separator sits just before the short frequency token,
        // so we skip the (possibly long) term instead of scanning it left-to-right.
        // 1BRC-style: process 8 bytes at a time using the "has-zero-byte" trick, so that
        // long terms do not dominate the scan (SWAR over JAVA_LONG_UNALIGNED).
        int sep = -1;
        final long TAB_MASK = 0x0909090909090909L;
        final long SEMI_MASK = 0x3B3B3B3B3B3B3B3BL;
        final long ONE_MASK = 0x0101010101010101L;
        final long HIGH_MASK = 0x8080808080808080L;
        final int regionLen = end - start;
        final int tailBytes = regionLen & LONG_ALIGNMENT_MASK;
        int idx = end - 1;
        // 1) unaligned tail (at most 7 bytes) byte-by-byte, right-to-left
        for (int t = 0; t < tailBytes && idx >= start; t++, idx--) {
            final byte b = source.get(ValueLayout.JAVA_BYTE, idx);
            if ((b ^ TAB_BYTE) == 0 || b == ';') {
                sep = idx;
                break;
            }
        }
        // 2) full 8-byte chunks, right-to-left
        if (sep < 0) {
            final int numFullChunks = regionLen >>> LONG_SIZE_BITS;
            for (int c = numFullChunks - 1; c >= 0; c--) {
                final int chunkStart = start + (c << LONG_SIZE_BITS);
                final long word = source.get(ValueLayout.JAVA_LONG_UNALIGNED, chunkStart);
                final long tabXor = word ^ TAB_MASK;
                final long semiXor = word ^ SEMI_MASK;
                final long tabZero = (tabXor - ONE_MASK) & ~tabXor & HIGH_MASK;
                final long semiZero = (semiXor - ONE_MASK) & ~semiXor & HIGH_MASK;
                final long matches = tabZero | semiZero;
                if (matches != 0) {
                    // leading zeros -> highest-index byte in the chunk (rightmost match)
                    final int bitPos = 63 - Long.numberOfLeadingZeros(matches);
                    sep = chunkStart + (bitPos >>> LONG_SIZE_BITS);
                    break;
                }
            }
        }
        int termEnd;
        int freqStart;
        if (sep >= 0) {
            termEnd = sep;
            freqStart = sep + 1;
            while (freqStart < end && isWhitespace(source.get(ValueLayout.JAVA_BYTE, freqStart))) {
                freqStart++;
            }
            while (termEnd > start && isWhitespace(source.get(ValueLayout.JAVA_BYTE, termEnd - 1))) {
                termEnd--;
            }
        } else {
            int cursor = end - 1;
            while (cursor >= start && !isWhitespace(source.get(ValueLayout.JAVA_BYTE, cursor))) {
                cursor--;
            }
            freqStart = cursor + 1;
            termEnd = cursor;
            while (termEnd > start && isWhitespace(source.get(ValueLayout.JAVA_BYTE, termEnd - 1))) {
                termEnd--;
            }
        }
        if (termEnd <= start || freqStart >= end) {
            return;
        }
        final String term;
        try {
            final byte[] termBytes = new byte[termEnd - start];
            for (int i = 0; i < termBytes.length; i++) {
                termBytes[i] = source.get(ValueLayout.JAVA_BYTE, start + i);
            }
            term = new String(termBytes, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return;
        }
        if (term.isEmpty()) {
            return;
        }
        final int freqEnd = end;
        final double frequency;
        try {
            boolean negative = false;
            int i = freqStart;
            if (i < freqEnd) {
                final byte first = source.get(ValueLayout.JAVA_BYTE, i);
                if (first == '-') {
                    negative = true;
                    i++;
                } else if (first == '+') {
                    i++;
                }
            }
            if (i >= freqEnd) {
                return;
            }
            boolean allDigits = true;
            long val = 0;
            for (int j = i; j < freqEnd; j++) {
                final byte db = source.get(ValueLayout.JAVA_BYTE, j);
                // bitwise digit detection: (db ^ 0x30) < 10  <=>  '0' <= db <= '9'
                if (((db ^ ASCII_ZERO) & BYTE_MASK) >= DECIMAL_BASE) {
                    allDigits = false;
                    break;
                }
                final int digit = db & DIGIT_MASK;
                // multiply by 10 via bit shifts: val*10 = (val<<3) + (val<<1)
                val = (val << LONG_SIZE_BITS) + (val << 1) + digit;
            }
            if (allDigits) {
                frequency = negative ? -val : val;
            } else {
                final StringBuilder sb = new StringBuilder(freqEnd - freqStart);
                for (int j = freqStart; j < freqEnd; j++) {
                    sb.append((char) (source.get(ValueLayout.JAVA_BYTE, j) & BYTE_MASK));
                }
                frequency = Double.parseDouble(sb.toString());
            }
        } catch (NumberFormatException ignored) {
            return;
        } catch (Exception ignored) {
            return;
        }
        result.add(new DictionaryItem(term, frequency, -1.0));
    }

    private static boolean isWhitespace(final byte b) {
        return b == ' ' || b == '\t' || b == '\n' || b == '\r' || b == '\f';
    }

    private static long findEither(final MemorySegment source, final int startIn, final int end, final byte first,
            final byte second) {
        int start = startIn;
        while (start <= end - LONG_BYTES) {
            final long word = source.get(ValueLayout.JAVA_LONG_UNALIGNED, start);
            final long a = word ^ (first * LOW_BITS_ONE);
            final long b = word ^ (second * LOW_BITS_ONE);
            final long matches = ((a - LOW_BITS_ONE) & ~a | (b - LOW_BITS_ONE) & ~b)
                    & HIGH_BITS;
            if (matches != 0) {
                return start + (Long.numberOfTrailingZeros(matches) >>> LONG_SIZE_BITS);
            }
            start += LONG_BYTES;
        }
        while (start < end) {
            final byte value = source.get(ValueLayout.JAVA_BYTE, start);
            if (value == first || value == second) {
                break;
            }
            start++;
        }
        return start;
    }
}
