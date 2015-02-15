/*-
 * $Id$
 */
package com.github.unix_junkie.javafs;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * @author Andrew ``Bass'' Shcheglov &lt;mailto:andrewbass@gmail.com&gt;
 */
@RunWith(JUnit4.class)
public final class PosixAttributesTest {
	@Test
	@SuppressWarnings("static-method")
	public void test() {
		assertEquals("---------", new PosixAttributes(0).toString());
		assertEquals("rw-------", new PosixAttributes(0600).toString());
		assertEquals("rw-r--r--", new PosixAttributes(0644).toString());
		assertEquals("rwx------", new PosixAttributes(0700).toString());
		assertEquals("rwxr-xr-x", new PosixAttributes(0755).toString());
		assertEquals("rwxrwxrwx", new PosixAttributes(0777).toString());
		assertEquals("--s------", new PosixAttributes(04000).toString());
		assertEquals("-----s---", new PosixAttributes(02000).toString());
		assertEquals("--------t", new PosixAttributes(01000).toString());
		assertEquals("rwsrwsrwt", new PosixAttributes(07777).toString());
	}

	@Test
	@SuppressWarnings("static-method")
	public void testMasking() {
		assertEquals(0600, new PosixAttributes(0100600).getValue());
		assertEquals(0600, new PosixAttributes(070600).getValue());
		assertEquals(0600, new PosixAttributes(0770600).getValue());
		assertEquals(0600, new PosixAttributes(07770600).getValue());
		assertEquals(0600, new PosixAttributes(077770600).getValue());
		assertEquals(0600, new PosixAttributes(0777770600).getValue());
		assertEquals(0600, new PosixAttributes(07777770600).getValue());
	}
}
