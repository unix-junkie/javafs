/*-
 * $Id$
 */
package com.github.unix_junkie.javafs;

import static com.github.unix_junkie.javafs.BlockSize.guessBlockSize;
import static com.github.unix_junkie.javafs.FileSystem.getBlockAddressSize;
import static java.lang.System.getProperty;
import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.Files.createTempFile;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.isRegularFile;
import static java.nio.file.Files.isSymbolicLink;
import static java.nio.file.Files.readAllBytes;
import static java.nio.file.Files.walkFileTree;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.Paths.get;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.LogManager;

import org.hamcrest.core.IsInstanceOf;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * @author Andrew ``Bass'' Shcheglov &lt;mailto:andrewbass@gmail.com&gt;
 */
@RunWith(JUnit4.class)
public final class FileSystemTest {
	@BeforeClass
	public static void oneTimeSetUp() throws IOException {
		LogManager.getLogManager().readConfiguration(Main.class.getResourceAsStream("/logging.properties"));
	}

	@Test
	@SuppressWarnings("static-method")
	public void testGuessBlockSize() {
		try {
			guessBlockSize(-1L);
			fail("Expecting an IllegalArgumentException");
		} catch (final AssertionError ae) {
			throw ae;
		} catch (final Throwable t) {
			assertThat(t, IsInstanceOf.instanceOf(IllegalArgumentException.class));
		}

		try {
			guessBlockSize(0L);
			fail("Expecting an IllegalArgumentException");
		} catch (final AssertionError ae) {
			throw ae;
		} catch (final Throwable t) {
			assertThat(t, IsInstanceOf.instanceOf(IllegalArgumentException.class));
		}

		try {
			guessBlockSize(BlockSize.getMinimum().getLength() - 1);
			fail("Expecting an IllegalArgumentException");
		} catch (final AssertionError ae) {
			throw ae;
		} catch (final Throwable t) {
			assertThat(t, IsInstanceOf.instanceOf(IllegalArgumentException.class));
		}

		final long length = Long.MAX_VALUE;
		final BlockSize blockSize = guessBlockSize(length);
		assertNotNull(blockSize);
		assertThat(Long.valueOf(blockSize.getLength()), lessThanOrEqualTo(Long.valueOf(length)));
	}

	@Test
	@SuppressWarnings("static-method")
	public void testBlockAddressSize() {
		assertEquals(1, getBlockAddressSize(1));
		assertEquals(1, getBlockAddressSize(0xFF));

		assertEquals(2, getBlockAddressSize(0x100));
		assertEquals(2, getBlockAddressSize(0x101));
		assertEquals(2, getBlockAddressSize(0xFFFF));

		assertEquals(3, getBlockAddressSize(0x10000));
		assertEquals(3, getBlockAddressSize(0x10001));
		assertEquals(3, getBlockAddressSize(0xFFFFFF));

		assertEquals(4, getBlockAddressSize(0x1000000));
		assertEquals(4, getBlockAddressSize(0x1000001));
		assertEquals(4, getBlockAddressSize(0xFFFFFFFFL));

		assertEquals(5, getBlockAddressSize(0x100000000L));
		assertEquals(5, getBlockAddressSize(0x100000001L));
		assertEquals(5, getBlockAddressSize(0xFFFFFFFFFFL));

		assertEquals(6, getBlockAddressSize(0x10000000000L));
		assertEquals(6, getBlockAddressSize(0x10000000001L));
		assertEquals(6, getBlockAddressSize(0xFFFFFFFFFFFFL));

		assertEquals(7, getBlockAddressSize(0x1000000000000L));
		assertEquals(7, getBlockAddressSize(0x1000000000001L));
		assertEquals(7, getBlockAddressSize(0xFFFFFFFFFFFFFFL));

		assertEquals(8, getBlockAddressSize(0x100000000000000L));
		assertEquals(8, getBlockAddressSize(0x100000000000001L));
		assertEquals(8, getBlockAddressSize(Long.MAX_VALUE));
	}

	@Test
	@SuppressWarnings("static-method")
	public void testAddDirectory() throws IOException {
		final Path p = createTempFile(null, ".javafs");
		try (final FileSystem fs = FileSystem.create(p, 1024L * 1024 - 1)) {
			final FileSystemEntry root = fs.getRoot();
			assertEquals("Empty directory should have a zero size", 0, root.getSize());

			System.out.println(root);
			final String name = newUniqueName(50);
			root.addChild(FileSystemEntry.newDirectory(name));

			try {
				root.addChild(FileSystemEntry.newDirectory(name));
			} catch (final AssertionError ae) {
				throw ae;
			} catch (final Throwable t) {
				assertThat(t, IsInstanceOf.instanceOf(IOException.class));
			}

			for (int i = 0; i < 30; i++) {
				root.addChild(FileSystemEntry.newDirectory(newUniqueName(50)));
			}

			root.addChild(FileSystemEntry.newDirectory("This is a very long name. File names can be longer than 255 characters and are Unicode-capable: \u041c\u0430\u043c\u0430 \u043c\u044b\u043b\u0430 \u0440\u0430\u043c\u0443 " + newUniqueName(255)));

			/*
			 * Re-read the root directory: make sure all values come from disk.
			 */
			final FileSystemEntry root2 = fs.getRoot();
			System.out.println(root2);
			assertNotEquals(0, root2.getSize());
			assertEquals(root.getSize(), root2.getSize());

			final Set<FileSystemEntry> children = root2.list();
			assertEquals(32, children.size());
			for (final FileSystemEntry child : children) {
				System.out.println(child);
			}
		}
	}

	@Test
	@SuppressWarnings("static-method")
	public void testAddReadFile() throws IOException, NoSuchAlgorithmException {
		final Path p = createTempFile(null, ".javafs");
		try (final FileSystem fs = FileSystem.create(p, 1024L * 1024 - 1)) {
			final Map<String, String> sha1sums = new LinkedHashMap<>();

			final MessageDigest md = MessageDigest.getInstance("SHA1");
			walkFileTree(get(getProperty("user.dir", ".")), new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(final Path file,
						final BasicFileAttributes attrs)
				throws IOException {
					final String fileName = file.getFileName().toString();
					if (isRegularFile(file, NOFOLLOW_LINKS) && fileName.endsWith(".java")) {
						md.reset();
						md.update(readAllBytes(file));
						final String sha1sum = toHexString(md.digest());
						System.out.println(sha1sum + ' ' + fileName);
						sha1sums.put(fileName, sha1sum);
						fs.getRoot().addChild(new FileSystemEntry(file));
					}
					return CONTINUE;
				}
			});

			final FileSystemEntry root = fs.getRoot();
			System.out.println(root);
			final Set<FileSystemEntry> children = root.list();

			assertEquals(sha1sums.size(), children.size());

			for (final FileSystemEntry child : children) {
				System.out.println(child);
				final ByteBuffer contents = ByteBuffer.allocate((int) child.getSize());
				child.writeDataTo(contents);
				contents.flip();
				md.reset();
				md.update(contents);
				final String sha1sum = toHexString(md.digest());
				assertEquals(sha1sums.get(child.getName()), sha1sum);
			}
		}
	}

	@Test
	@SuppressWarnings("static-method")
	public void testUnlinkFile() throws IOException {
		final Path p = createTempFile(null, ".javafs");
		try (final FileSystem fs = FileSystem.create(p, 1024L * 1024 - 1)) {
			walkFileTree(get(getProperty("user.dir", ".")), new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(final Path file,
						final BasicFileAttributes attrs)
				throws IOException {
					final String fileName = file.getFileName().toString();
					if (isRegularFile(file, NOFOLLOW_LINKS) && fileName.endsWith(".java")) {
						fs.getRoot().addChild(new FileSystemEntry(file));
					}
					return CONTINUE;
				}
			});

			final FileSystemEntry root = fs.getRoot();

			try {
				root.unlink("foo");
			} catch (final AssertionError ae) {
				throw ae;
			} catch (final Throwable t) {
				assertThat(t, IsInstanceOf.instanceOf(IOException.class));
			}

			final long oldFileCount = fs.getFileCount();
			final int oldRootChildrenCount = root.list().size();
			final long oldFreeBlockCount = fs.getFreeBlockCount();
			final long oldRootSize = root.getSize();

			assertEquals(oldFileCount, oldRootChildrenCount + 1);

			root.unlink(root.list().iterator().next().getName());

			final long newFileCount = fs.getFileCount();
			final int newRootChildrenCount = root.list().size();
			final long newFreeBlockCount = fs.getFreeBlockCount();
			final long newRootSize = root.getSize();

			assertEquals(newFileCount, newRootChildrenCount + 1);

			assertEquals(1, oldFileCount - newFileCount);
			assertEquals(1, oldRootChildrenCount - newRootChildrenCount);
			assertTrue(oldFreeBlockCount < newFreeBlockCount);
			assertTrue(oldRootSize > newRootSize);
		}
	}

	@Test
	@SuppressWarnings("static-method")
	public void testSymlinkSupport() throws IOException {
		final Path source = get(getProperty("user.home"), ".Xdefaults");
		assumeTrue(exists(source, NOFOLLOW_LINKS));
		assumeTrue(isSymbolicLink(source));

		final Path p = createTempFile(null, ".javafs");
		try (final FileSystem fs = FileSystem.create(p, 1024L * 1024 - 1)) {
			fs.getRoot().addChild(new FileSystemEntry(source));
		}
	}

	public static String toHexString(final byte bytes[]) {
		final char hexArray[] = "0123456789abcdef".toCharArray();
		final char hexChars[] = new char[bytes.length * 2];
		for (int i = 0, n = bytes.length; i < n; i++) {
			final int b = bytes[i] & 0xFF;
			hexChars[i * 2] = hexArray[b >>> 4];
			hexChars[i * 2 + 1] = hexArray[b & 0x0F];
		}
		return CharBuffer.wrap(hexChars).toString();
	}

	private static String newUniqueName(final int minimumLength) {
		final StringBuilder suffix = new StringBuilder().append('.').append(SUFFIX_GENERATOR.incrementAndGet());
		if (suffix.length() >= minimumLength) {
			return suffix.toString();
		}

		final StringBuilder name = new StringBuilder();
		for (int i = 0; i < minimumLength - suffix.length(); i++) {
			name.append((char) ('a' + (char) (i % 26)));
		}
		name.append(suffix);
		return name.toString();
	}

	private static final AtomicLong SUFFIX_GENERATOR = new AtomicLong();
}
