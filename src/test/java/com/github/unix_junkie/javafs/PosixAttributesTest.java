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
		assertEquals("---------", new PosixAttributes((short) 0).toString());
		assertEquals("rw-------", new PosixAttributes((short) 0600).toString());
		assertEquals("rw-r--r--", new PosixAttributes((short) 0644).toString());
		assertEquals("rwx------", new PosixAttributes((short) 0700).toString());
		assertEquals("rwxr-xr-x", new PosixAttributes((short) 0755).toString());
		assertEquals("rwxrwxrwx", new PosixAttributes((short) 0777).toString());
		assertEquals("--s------", new PosixAttributes((short) 04000).toString());
		assertEquals("-----s---", new PosixAttributes((short) 02000).toString());
		assertEquals("--------t", new PosixAttributes((short) 01000).toString());
		assertEquals("rwsrwsrwt", new PosixAttributes((short) 07777).toString());
	}
}
