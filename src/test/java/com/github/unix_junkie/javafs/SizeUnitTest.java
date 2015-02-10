/*-
 * $Id$
 */
package com.github.unix_junkie.javafs;

import static com.github.unix_junkie.javafs.SizeUnit.E;
import static com.github.unix_junkie.javafs.SizeUnit.G;
import static com.github.unix_junkie.javafs.SizeUnit.K;
import static com.github.unix_junkie.javafs.SizeUnit.M;
import static com.github.unix_junkie.javafs.SizeUnit.P;
import static com.github.unix_junkie.javafs.SizeUnit.T;
import static com.github.unix_junkie.javafs.SizeUnit.parseSize;
import static com.github.unix_junkie.javafs.SizeUnit.toBytes;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import org.hamcrest.core.IsInstanceOf;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * @author Andrew ``Bass'' Shcheglov &lt;mailto:andrewbass@gmail.com&gt;
 */
@RunWith(JUnit4.class)
public final class SizeUnitTest {
	@Test
	@SuppressWarnings("static-method")
	public void testparseSize() {
		try {
			parseSize(null);
			fail("Expecting an IllegalArgumentException");
		} catch (final AssertionError ae) {
			throw ae;
		} catch (final Throwable t) {
			assertThat(t, IsInstanceOf.instanceOf(IllegalArgumentException.class));
		}

		try {
			parseSize("");
			fail("Expecting an IllegalArgumentException");
		} catch (final AssertionError ae) {
			throw ae;
		} catch (final Throwable t) {
			assertThat(t, IsInstanceOf.instanceOf(IllegalArgumentException.class));
		}

		try {
			parseSize("BBB");
			fail("Expecting an IllegalArgumentException");
		} catch (final AssertionError ae) {
			throw ae;
		} catch (final Throwable t) {
			assertThat(t, IsInstanceOf.instanceOf(IllegalArgumentException.class));
		}

		assertEquals(999, parseSize("999"));
		assertEquals(999, parseSize("999b"));
		assertEquals(999, parseSize("999B"));

		assertEquals(toBytes(1, K), parseSize("1k"));
		assertEquals(toBytes(2, K), parseSize("2k"));
		assertEquals(toBytes(3, K), parseSize("3k"));

		assertEquals(toBytes(1, M), parseSize("1M"));
		assertEquals(toBytes(1, G), parseSize("1G"));
		assertEquals(toBytes(1, T), parseSize("1T"));
		assertEquals(toBytes(1, P), parseSize("1P"));
		assertEquals(toBytes(1, E), parseSize("1E"));
	}

	@Test
	@SuppressWarnings("static-method")
	public void testToString() {
		assertEquals("0B", SizeUnit.toString(0L));
		assertEquals("2B", SizeUnit.toString(2L));
		assertEquals("999B", SizeUnit.toString(999L));
		assertEquals("1K", SizeUnit.toString(1023L));
		assertEquals("1K", SizeUnit.toString(1024L));
		assertEquals("1025B", SizeUnit.toString(1025L));

		assertEquals("1M", SizeUnit.toString(toBytes(1, M)));
		assertEquals("2M", SizeUnit.toString(toBytes(2, M)));
		assertEquals("3M", SizeUnit.toString(toBytes(3, M)));
		assertEquals("4M", SizeUnit.toString(toBytes(4, M)));
		assertEquals("5M", SizeUnit.toString(toBytes(5, M)));
		assertEquals("6M", SizeUnit.toString(toBytes(6, M)));
		assertEquals("7M", SizeUnit.toString(toBytes(7, M)));
		assertEquals("8M", SizeUnit.toString(toBytes(8, M)));
		assertEquals("1022M", SizeUnit.toString(toBytes(1022, M)));
		assertEquals("1023M", SizeUnit.toString(toBytes(1023, M)));
		assertEquals("1G", SizeUnit.toString(toBytes(1024, M) - 1));
		assertEquals("1G", SizeUnit.toString(toBytes(1024, M)));
		assertEquals("1025M", SizeUnit.toString(toBytes(1025, M)));

		assertEquals("128B", SizeUnit.toString(Byte.MAX_VALUE));
		assertEquals("32K", SizeUnit.toString(Short.MAX_VALUE));
		assertEquals("64K", SizeUnit.toString(Character.MAX_VALUE));
		assertEquals("2G", SizeUnit.toString(Integer.MAX_VALUE));
		assertEquals("8E", SizeUnit.toString(Long.MAX_VALUE));
	}
}
