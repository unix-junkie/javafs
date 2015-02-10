/*-
 * $Id$
 */
package com.github.unix_junkie.javafs;

import static java.lang.String.format;

import java.util.concurrent.Callable;

/**
 * @author Andrew ``Bass'' Shcheglov &lt;mailto:andrewbass@gmail.com&gt;
 */
public enum SizeUnit {
	B,
	K,
	M,
	G,
	T,
	P,
	E,
	;

	public static long toBytes(final long size, final SizeUnit unit) {
		// TODO: check for overflow, but still support the 8E limit.
		switch (unit) {
		case B:
			return size;
		case K:
			return toBytes(size * 1024, B);
		case M:
			return toBytes(size * 1024, K);
		case G:
			return toBytes(size * 1024, M);
		case T:
			return toBytes(size * 1024, G);
		case P:
			return toBytes(size * 1024, T);
		case E:
			return toBytes(size * 1024, P);
		default:
			throw new IllegalArgumentException(format("Unsupported size: %d%s", Long.valueOf(size), unit));
		}
	}

	public static long parseSize(final String s) {
		if (s == null || s.length() == 0) {
			throw new IllegalArgumentException("Size is empty");
		}

		final char suffix = s.charAt(s.length() - 1);
		try {
			if ('0' <= suffix && suffix <= '9') {
				/*
				 * No alphabetical suffix.
				 */
				return Long.parseLong(s);
			}

			/*
			 * Defer size evaluation: if the suffix is invalid,
			 * we'll boil out with that error first.
			 */
			final Callable<Long> size = () -> Long.valueOf(s.substring(0, s.length() - 1));

			switch (suffix) {
			case 'b':
			case 'B':
				return toBytes(size.call().longValue(), B);
			case 'k':
			case 'K':
				return toBytes(size.call().longValue(), K);
			case 'm':
			case 'M':
				return toBytes(size.call().longValue(), M);
			case 'g':
			case 'G':
				return toBytes(size.call().longValue(), G);
			case 't':
			case 'T':
				return toBytes(size.call().longValue(), T);
			case 'p':
			case 'P':
				return toBytes(size.call().longValue(), P);
			case 'e':
			case 'E':
				return toBytes(size.call().longValue(), E);
			default:
				throw new IllegalArgumentException(format("Invalid size suffix: \"%s\" in input string: \"%s\"", String.valueOf(suffix), s));
			}
		} catch (final IllegalArgumentException iae) {
			throw iae;
		} catch (final Exception e) {
			throw new IllegalArgumentException(e.getMessage(), e);
		}
	}

	public static String toString(final long size) {
		if (size == 0L) {
			return String.valueOf(size) + B;
		}

		if ((size & size + 1) == 0) {
			/*
			 * A power of 2 minus 1.
			 */
			return toString(size + 1);
		}

		final int ordinal = Long.numberOfTrailingZeros(size) / 10;
		/*
		 * Shift with zero fill is used in order for Long.MAX_VALUE
		 * to be represented as "8E" rather than "-8E".
		 */
		return String.valueOf(size >>> ordinal * 10) + SizeUnit.values()[ordinal];
	}
}
