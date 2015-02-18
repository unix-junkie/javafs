/*-
 * $Id$
 */
package com.github.unix_junkie.javafs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Date;

import javax.annotation.Nonnull;

import org.hamcrest.core.IsInstanceOf;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * @author Andrew ``Bass'' Shcheglov &lt;mailto:andrewbass@gmail.com&gt;
 */
@RunWith(JUnit4.class)
public final class FileSystemEntryTest {
	@Test
	@SuppressWarnings("static-method")
	public void testBinaryFormat() throws IOException {
		final FileSystemEntry link = new SymbolicLink(new PosixAttributes((short) 0777), (byte) 1, (short) 1021, (short) 1021, 11, new Date(), new Date(), new Date(), ".Xdefaults");
		@Nonnull
		@SuppressWarnings("null")
		final ByteBuffer buffer0 = ByteBuffer.allocate(link.getMetadataSize());
		link.writeMetadataTo(buffer0);
		buffer0.flip();
		assertEquals(link.toString(), FileSystemEntry.readMetadataFrom(buffer0).toString());

		final FileSystemEntry file = new File(new PosixAttributes((short) 0644), (byte) 1, (short) 1021, (short) 1021, 47, new Date(), new Date(), new Date(), ".Xresources");
		@Nonnull
		@SuppressWarnings("null")
		final ByteBuffer buffer1 = ByteBuffer.allocate(file.getMetadataSize());
		file.writeMetadataTo(buffer1);
		buffer1.flip();
		assertEquals(file.toString(), FileSystemEntry.readMetadataFrom(buffer1).toString());

		final FileSystemEntry directory = new Directory(new PosixAttributes((short) 01777), (byte) 1, (short) 0, (short) 0, 4096, new Date(), new Date(), new Date(), "tmp");
		@Nonnull
		@SuppressWarnings("null")
		final ByteBuffer buffer2 = ByteBuffer.allocate(directory.getMetadataSize());
		directory.writeMetadataTo(buffer2);
		buffer2.flip();
		assertEquals(directory.toString(), FileSystemEntry.readMetadataFrom(buffer2).toString());
	}

	@Test
	@SuppressWarnings({"static-method", "unused"})
	public void testInvalidName() {
		try {
			new File(new PosixAttributes((short) 0644), (byte) 1, (short) 1021, (short) 1021, 47, new Date(), new Date(), new Date(), "foo/bar");
			fail("Expecting an IllegalArgumentException");
		} catch (final AssertionError ae) {
			throw ae;
		} catch (final Throwable t) {
			assertThat(t, IsInstanceOf.instanceOf(IllegalArgumentException.class));
		}

		try {
			new File(new PosixAttributes((short) 0644), (byte) 1, (short) 1021, (short) 1021, 47, new Date(), new Date(), new Date(), "foo\0bar");
			fail("Expecting an IllegalArgumentException");
		} catch (final AssertionError ae) {
			throw ae;
		} catch (final Throwable t) {
			assertThat(t, IsInstanceOf.instanceOf(IllegalArgumentException.class));
		}

		new Directory(new PosixAttributes((short) 0755), (byte) 1, (short) 1021, (short) 1021, 47, new Date(), new Date(), new Date(), ".");
		try {
			new File(new PosixAttributes((short) 0755), (byte) 1, (short) 1021, (short) 1021, 47, new Date(), new Date(), new Date(), ".");
			fail("Expecting an IllegalArgumentException");
		} catch (final AssertionError ae) {
			throw ae;
		} catch (final Throwable t) {
			assertThat(t, IsInstanceOf.instanceOf(IllegalArgumentException.class));
		}
		try {
			new SymbolicLink(new PosixAttributes((short) 0755), (byte) 1, (short) 1021, (short) 1021, 47, new Date(), new Date(), new Date(), ".");
			fail("Expecting an IllegalArgumentException");
		} catch (final AssertionError ae) {
			throw ae;
		} catch (final Throwable t) {
			assertThat(t, IsInstanceOf.instanceOf(IllegalArgumentException.class));
		}

		new Directory(new PosixAttributes((short) 0755), (byte) 1, (short) 1021, (short) 1021, 47, new Date(), new Date(), new Date(), "..");
		try {
			new File(new PosixAttributes((short) 0755), (byte) 1, (short) 1021, (short) 1021, 47, new Date(), new Date(), new Date(), "..");
			fail("Expecting an IllegalArgumentException");
		} catch (final AssertionError ae) {
			throw ae;
		} catch (final Throwable t) {
			assertThat(t, IsInstanceOf.instanceOf(IllegalArgumentException.class));
		}
		try {
			new SymbolicLink(new PosixAttributes((short) 0755), (byte) 1, (short) 1021, (short) 1021, 47, new Date(), new Date(), new Date(), "..");
			fail("Expecting an IllegalArgumentException");
		} catch (final AssertionError ae) {
			throw ae;
		} catch (final Throwable t) {
			assertThat(t, IsInstanceOf.instanceOf(IllegalArgumentException.class));
		}
	}
}
