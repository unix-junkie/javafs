/*-
 * $Id$
 */
package com.github.unix_junkie.javafs;

import static com.github.unix_junkie.javafs.FileUtilities.getPosixAttributes;
import static java.lang.System.getProperty;
import static java.nio.file.Files.createTempDirectory;
import static java.nio.file.Files.createTempFile;
import static java.nio.file.Files.delete;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;

import java.io.IOException;
import java.nio.file.Path;

import javax.annotation.Nonnull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * @author Andrew ``Bass'' Shcheglov &lt;mailto:andrewbass@gmail.com&gt;
 */
@RunWith(JUnit4.class)
public final class PosixAttributesTest {
	@Test
	@SuppressWarnings({ "static-method", "javadoc" })
	public void testToString() {
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
	@SuppressWarnings({ "static-method", "javadoc" })
	public void testMasking() {
		assertEquals(0600, new PosixAttributes(0100600).getValue());
		assertEquals(0600, new PosixAttributes(070600).getValue());
		assertEquals(0600, new PosixAttributes(0770600).getValue());
		assertEquals(0600, new PosixAttributes(07770600).getValue());
		assertEquals(0600, new PosixAttributes(077770600).getValue());
		assertEquals(0600, new PosixAttributes(0777770600).getValue());
		assertEquals(0600, new PosixAttributes(07777770600).getValue());
	}

	@Test
	@SuppressWarnings({ "static-method", "javadoc" })
	public void testFileAttributes() throws IOException {
		assumeFalse("On Windows, files are executable by default", getProperty("os.name").startsWith("Windows"));
		@Nonnull
		@SuppressWarnings("null")
		final Path tempFile = createTempFile(null, null);
		try {
			/*
			 * Permissions for 'g'roup and 'o'ther may vary.
			 */
			final short ownerAttrs = (short) (getPosixAttributes(tempFile).getValue() & 07700);
			assertEquals(0600, ownerAttrs);
		} finally {
			delete(tempFile);
		}
	}

	@Test
	@SuppressWarnings({ "static-method", "javadoc" })
	public void testDirectoryAttributes() throws IOException {
		@Nonnull
		@SuppressWarnings("null")
		final Path tempDirectory = createTempDirectory(null);
		try {
			/*
			 * Permissions for 'g'roup and 'o'ther may vary.
			 */
			final short ownerAttrs = (short) (getPosixAttributes(tempDirectory).getValue() & 07700);
			assertEquals(0700, ownerAttrs);
		} finally {
			delete(tempDirectory);
		}
	}
}
