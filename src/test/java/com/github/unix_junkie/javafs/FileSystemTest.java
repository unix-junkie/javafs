/*-
 * $Id$
 */
package com.github.unix_junkie.javafs;

import static com.github.unix_junkie.javafs.BlockSize.guessBlockSize;
import static com.github.unix_junkie.javafs.FileSystem.getBlockAddressSize;
import static com.github.unix_junkie.javafs.FileUtilities.getBlockCount;
import static com.github.unix_junkie.javafs.FileUtilities.symbolicLinksSupported;
import static com.github.unix_junkie.javafs.FileUtilities.writeTo;
import static java.lang.System.getProperty;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.Files.createSymbolicLink;
import static java.nio.file.Files.createTempDirectory;
import static java.nio.file.Files.createTempFile;
import static java.nio.file.Files.delete;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.isRegularFile;
import static java.nio.file.Files.isSymbolicLink;
import static java.nio.file.Files.readAllBytes;
import static java.nio.file.Files.walkFileTree;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.Paths.get;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.LogManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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
		@Nonnull
		@SuppressWarnings("null")
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
		@Nonnull
		@SuppressWarnings("null")
		final Path p = createTempFile(null, ".javafs");
		try (final FileSystem fs = FileSystem.create(p, 1024L * 1024 - 1)) {
			final Map<String, String> sha1sums = new LinkedHashMap<>();

			final MessageDigest md = MessageDigest.getInstance("SHA1");
			walkFileTree(get(getProperty("user.dir", ".")), new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(@Nullable final Path file,
						@Nullable final BasicFileAttributes attrs)
				throws IOException {
					/*
					 * Useless but required for Eclipse null analysis.
					 */
					if (file == null) {
						return CONTINUE;
					}

					final String fileName = file.getFileName().toString();
					if (isRegularFile(file, NOFOLLOW_LINKS) && fileName.endsWith(".java")) {
						md.reset();
						md.update(readAllBytes(file));
						@Nonnull
						@SuppressWarnings("null")
						final byte[] digest = md.digest();
						final String sha1sum = toHexString(digest);
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
				@Nonnull
				@SuppressWarnings("null")
				final ByteBuffer contents = ByteBuffer.allocate((int) child.getSize());
				child.writeDataTo(contents);
				contents.flip();
				md.reset();
				md.update(contents);
				@Nonnull
				@SuppressWarnings("null")
				final byte digest[] = md.digest();
				final String sha1sum = toHexString(digest);
				assertEquals(sha1sums.get(child.getName()), sha1sum);
			}
		}
	}

	@Test
	@SuppressWarnings("static-method")
	public void testUnlinkFile() throws IOException {
		@Nonnull
		@SuppressWarnings("null")
		final Path p = createTempFile(null, ".javafs");
		try (final FileSystem fs = FileSystem.create(p, 1024L * 1024 - 1)) {
			walkFileTree(get(getProperty("user.dir", ".")), new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(@Nullable final Path file,
						@Nullable final BasicFileAttributes attrs)
				throws IOException {
					/*
					 * Useless but required for Eclipse null analysis.
					 */
					if (file == null) {
						return CONTINUE;
					}

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
	public void testFileSymlinkSupport() throws IOException {
		@Nonnull
		@SuppressWarnings("null")
		final Path tempFile = createTempFile(null, null);
		testSymlinkSupport(tempFile);
	}

	@Test
	@SuppressWarnings("static-method")
	public void testDirectorySymlinkSupport() throws IOException {
		@Nonnull
		@SuppressWarnings("null")
		final Path tempDir = createTempDirectory(null);
		testSymlinkSupport(tempDir);
	}

	private static void testSymlinkSupport(final Path target) throws IOException {
		assumeTrue(symbolicLinksSupported());

		@Nonnull
		@SuppressWarnings("null")
		final Path link = get(target.getParent().toString(), target.getFileName() + ".lnk");
		try {
			assertTrue(exists(target));
			assertFalse(exists(link));
			assertFalse(exists(link, NOFOLLOW_LINKS));

			createSymbolicLink(link, target);
			assertTrue(isSymbolicLink(link));

			assertTrue(exists(target));
			assertTrue(exists(link));
			assertTrue(exists(link, NOFOLLOW_LINKS));

			delete(target);

			assertFalse(exists(target));
			assertFalse(exists(link));
			assertTrue(exists(link, NOFOLLOW_LINKS));

			@Nonnull
			@SuppressWarnings("null")
			final Path p = createTempFile(null, ".javafs");
			try (final FileSystem fs = FileSystem.create(p, 1024L * 1024 - 1)) {
				final FileSystemEntry symlinkEntry0 = new FileSystemEntry(link);
				/*
				 * Read the target of a detached entry.
				 */
				final Path target0 = symlinkEntry0.getTarget();
				assertTrue(symlinkEntry0.isDetached());

				fs.getRoot().addChild(symlinkEntry0);

				/*
				 * Re-read the root directory: make sure all values come from disk.
				 */
				final FileSystemEntry root = fs.getRoot();
				System.out.println(root);

				final Set<FileSystemEntry> children = root.list();
				assertEquals(1, children.size());
				final FileSystemEntry symlinkEntry1 = children.iterator().next();

				System.out.println(symlinkEntry1);

				assertFalse(symlinkEntry1.isDetached());
				/*
				 * Read the target of an attached entry.
				 */
				final Path target1 = symlinkEntry1.getTarget();

				assertEquals(target0, target1);
			}
		} finally {
			delete(link);
		}
	}

	@Test
	@SuppressWarnings("static-method")
	public void testBlockCount() throws IOException {
		final long blockSize = 16;
		assertEquals(1, getBlockCount(0, blockSize));
		assertEquals(1, getBlockCount(5, blockSize));
		assertEquals(1, getBlockCount(16, blockSize));
		assertEquals(2, getBlockCount(17, blockSize));

		@Nonnull
		@SuppressWarnings("null")
		final Path p0 = createTempFile(null, ".javafs");
		try (final FileSystem fs = FileSystem.create(p0, 1024L * 1024 - 1)) {
			final FileSystemEntry root = fs.getRoot();
			assertEquals("Empty directory should have a zero size", 0, root.getSize());

			final String name = newUniqueName(50);
			root.addChild(FileSystemEntry.newDirectory(name));

			/*
			 * Re-read the root directory: make sure all values come from disk.
			 */
			assertEquals(fs.getBlockCount(0L), fs.getRoot().getBlockCount());

			System.out.println(root);
		}

		@Nonnull
		@SuppressWarnings("null")
		final Path p1 = createTempFile(null, ".javafs");
		try (final FileSystem fs = FileSystem.create(p1, 1024L * 1024 - 1)) {
			final FileSystemEntry root = fs.getRoot();
			assertEquals("Empty directory should have a zero size", 0, root.getSize());

			final String name = newUniqueName(2 * fs.getBlockSize().getLength());
			root.addChild(FileSystemEntry.newDirectory(name));

			/*
			 * Re-read the root directory: make sure all values come from disk.
			 */
			assertEquals(fs.getBlockCount(0L), fs.getRoot().getBlockCount());

			System.out.println(root);
		}
	}

	@Test
	@SuppressWarnings("static-method")
	public void testWriteTo() throws CharacterCodingException {
		final ByteBuffer entry0 = US_ASCII.newEncoder().encode(CharBuffer.wrap("Hello, World!"));
		final ByteBuffer entry1 = US_ASCII.newEncoder().encode(CharBuffer.wrap("A quick brown fox jumps over a lazy dog."));

		final int blockSize = 16;

		assertTrue(entry0.limit() <= blockSize);

		final ByteBuffer block0 = ByteBuffer.allocate(blockSize);
		block0.put(entry0);
		final int firstBlockBytesRemaining = block0.limit() - block0.position();

		final int bytesPending = entry1.limit() - entry1.position();

		assertTrue(bytesPending > firstBlockBytesRemaining);

		final long newBlocksRequired = getBlockCount(bytesPending - firstBlockBytesRemaining, blockSize);
		final List<ByteBuffer> blocks = new ArrayList<>();
		blocks.add(block0);
		for (long l = 0; l < newBlocksRequired; l++) {
			blocks.add(ByteBuffer.allocate(blockSize));
		}

		writeTo(entry1, blocks, entry0.limit());

		assertEquals("Hello, World!A q", US_ASCII.newDecoder().decode((ByteBuffer) blocks.get(0).flip()).toString());
		assertEquals("uick brown fox j", US_ASCII.newDecoder().decode((ByteBuffer) blocks.get(1).flip()).toString());
		assertEquals("umps over a lazy", US_ASCII.newDecoder().decode((ByteBuffer) blocks.get(2).flip()).toString());
		assertEquals(" dog.",            US_ASCII.newDecoder().decode((ByteBuffer) blocks.get(3).flip()).toString());
	}

	static String toHexString(final byte bytes[]) {
		final char hexArray[] = "0123456789abcdef".toCharArray();
		final char hexChars[] = new char[bytes.length * 2];
		for (int i = 0, n = bytes.length; i < n; i++) {
			final int b = bytes[i] & 0xFF;
			hexChars[i * 2] = hexArray[b >>> 4];
			hexChars[i * 2 + 1] = hexArray[b & 0x0F];
		}
		@Nonnull
		@SuppressWarnings("null")
		final String hexString = CharBuffer.wrap(hexChars).toString();
		return hexString;
	}

	private static String newUniqueName(final int minimumLength) {
		final StringBuilder suffix = new StringBuilder().append('.').append(SUFFIX_GENERATOR.incrementAndGet());
		if (suffix.length() >= minimumLength) {
			@Nonnull
			@SuppressWarnings("null")
			final String s = suffix.toString();
			return s;
		}

		final StringBuilder name = new StringBuilder();
		for (int i = 0; i < minimumLength - suffix.length(); i++) {
			name.append((char) ('a' + (char) (i % 26)));
		}
		name.append(suffix);
		@Nonnull
		@SuppressWarnings("null")
		final String s = name.toString();
		return s;
	}

	private static final AtomicLong SUFFIX_GENERATOR = new AtomicLong();
}
