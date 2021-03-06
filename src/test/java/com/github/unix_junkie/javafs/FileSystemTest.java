/*-
 * $Id$
 */
package com.github.unix_junkie.javafs;

import static com.github.unix_junkie.javafs.BlockSize.guessBlockSize;
import static com.github.unix_junkie.javafs.FileSystem.getBlockAddressSize;
import static com.github.unix_junkie.javafs.FileUtilities.getBlockCount;
import static com.github.unix_junkie.javafs.FileUtilities.symbolicLinksSupported;
import static com.github.unix_junkie.javafs.FileUtilities.writeTo;
import static java.lang.Runtime.getRuntime;
import static java.lang.System.gc;
import static java.lang.System.getProperty;
import static java.lang.System.nanoTime;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.sleep;
import static java.nio.channels.FileChannel.MapMode.READ_WRITE;
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
import static java.nio.file.Files.size;
import static java.nio.file.Files.walkFileTree;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.Paths.get;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.Assert.assertArrayEquals;
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
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.file.AccessDeniedException;
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
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.LogManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.hamcrest.core.IsInstanceOf;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * @author Andrew ``Bass'' Shcheglov &lt;mailto:andrewbass@gmail.com&gt;
 */
@RunWith(JUnit4.class)
public final class FileSystemTest {
	@BeforeClass
	@SuppressWarnings("javadoc")
	public static void oneTimeSetUp() throws IOException {
		LogManager.getLogManager().readConfiguration(Main.class.getResourceAsStream("/logging.properties"));
	}

	@Test
	@SuppressWarnings({ "static-method", "javadoc" })
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
	@SuppressWarnings({ "static-method", "javadoc" })
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
	@SuppressWarnings({ "static-method", "javadoc" })
	public void testAddDirectory() throws IOException {
		@Nonnull
		@SuppressWarnings("null")
		final Path p = createTempFile(null, ".javafs");
		try (final FileSystem fs = FileSystem.create(p, 1024L * 1024 - 1)) {
			final Directory root = fs.getRoot();
			assertEquals("Empty directory should have a zero size", 0, root.getDataSize());

			System.out.println(root);
			final String name = newUniqueName(50);
			root.addChild(new Directory(name));

			try {
				root.addChild(new Directory(name));
			} catch (final AssertionError ae) {
				throw ae;
			} catch (final Throwable t) {
				assertThat(t, IsInstanceOf.instanceOf(IOException.class));
			}

			for (int i = 0; i < 30; i++) {
				root.addChild(new Directory(newUniqueName(50)));
			}

			root.addChild(new Directory("This is a very long name. File names can be longer than 255 characters and are Unicode-capable: \u041c\u0430\u043c\u0430 \u043c\u044b\u043b\u0430 \u0440\u0430\u043c\u0443 " + newUniqueName(255)));

			/*
			 * Re-read the root directory: make sure all values come from disk.
			 */
			final Directory root2 = fs.getRoot();
			System.out.println(root2);
			assertNotEquals(0, root2.getDataSize());
			assertEquals(root.getDataSize(), root2.getDataSize());

			final Set<FileSystemEntry> children = root2.list();
			assertEquals(32, children.size());
			for (final FileSystemEntry child : children) {
				System.out.println(child);
			}
		}
	}

	@Test
	@SuppressWarnings({ "static-method", "javadoc" })
	public void testDirectoryGrowth() throws IOException {
		@Nonnull
		@SuppressWarnings("null")
		final Path p = createTempFile(null, ".javafs");
		try (final FileSystem fs = FileSystem.create(p, 1024L * 1024 - 1)) {
			final Directory root = fs.getRoot();
			assertEquals("Empty directory should have a zero size", 0, root.getDataSize());

			final String name = newUniqueName(2 * fs.getBlockSize().getLength());
			final FileSystemEntry child = new Directory(name);
			root.addChild(child);

			/*
			 * Re-read the root directory: make sure all values come from disk.
			 */
			final Directory root2 = fs.getRoot();
			assertEquals(fs.getBlockCount(0L), root2.getBlockCount());
			assertEquals(fs.getBlockAddressSize() + child.getMetadataSize(), root2.getDataSize());
			System.out.println(root2);
			System.out.println(root2.list().iterator().next());
		}
	}

	@Test
	@SuppressWarnings({ "static-method", "javadoc" })
	public void testAddReadFile() throws IOException, NoSuchAlgorithmException {
		final Map<String, String> sha1sums = new LinkedHashMap<>();

		final MessageDigest md = MessageDigest.getInstance("SHA1");

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
						md.reset();
						md.update(readAllBytes(file));
						@Nonnull
						@SuppressWarnings("null")
						final byte[] digest = md.digest();
						final String sha1sum = toHexString(digest);
						System.out.println(sha1sum + ' ' + fileName);
						sha1sums.put(fileName, sha1sum);
						fs.getRoot().addChild(new File(file));
					}
					return CONTINUE;
				}
			});
		}

		/*
		 * Re-mount the file system.
		 */
		try (final FileSystem fs = FileSystem.mount(p)) {
			final Directory root = fs.getRoot();
			System.out.println(root);
			final Set<FileSystemEntry> children = root.list();

			assertEquals(sha1sums.size(), children.size());

			for (final FileSystemEntry child : children) {
				System.out.println(child);
				md.reset();
				md.update(child.getData());
				@Nonnull
				@SuppressWarnings("null")
				final byte digest[] = md.digest();
				final String sha1sum = toHexString(digest);
				assertEquals(sha1sums.get(child.getName()), sha1sum);
			}
		}
	}

	@Test
	@SuppressWarnings({ "static-method", "javadoc" })
	public void testEmptyFile() throws NoSuchAlgorithmException, IOException {
		@Nonnull
		@SuppressWarnings("null")
		final Path file = createTempFile(null, null);

		final MessageDigest md = MessageDigest.getInstance("SHA1");

		md.reset();
		md.update(readAllBytes(file));
		final byte digest[] = md.digest();

		@Nonnull
		@SuppressWarnings("null")
		final Path p = createTempFile(null, ".javafs");
		try (final FileSystem fs = FileSystem.create(p, 1024L * 1024 - 1)) {
			fs.getRoot().addChild(new File(file));

			final Directory root = fs.getRoot();
			System.out.println(root);
			final FileSystemEntry child = root.list().iterator().next();

			md.reset();
			md.update(child.getData());
			assertArrayEquals(digest, md.digest());
		}
	}

	@Ignore("Not yet implemented")
	@Test
	@SuppressWarnings({ "static-method", "javadoc" })
	public void testLargeFile() throws IOException, NoSuchAlgorithmException {
		@Nonnull
		@SuppressWarnings("null")
		final Path file = createTempFile(null, null);
		try {
			final MessageDigest md = MessageDigest.getInstance("SHA1");
			@Nonnull
			final byte digest[];

			try (final FileChannel channel = FileChannel.open(file, READ, WRITE)) {
				final Random random = new Random();
				final MappedByteBuffer tail = channel.map(READ_WRITE, Integer.MAX_VALUE, 1);
				tail.put((byte) 0x0);

				/*
				 * When filling a 2G file, block size of 65k is enough.
				 */
				final int blockSize = 0x10000;

				final long fileSize = size(file);
				assertEquals(0, fileSize % blockSize);
				final ByteBuffer block = ByteBuffer.allocate(blockSize);
				final long blockCount = fileSize / blockSize;
				final long t0 = nanoTime();
				try {
					md.reset();

					for (long blockId = 0; blockId < blockCount; blockId++) {
						final MappedByteBuffer mappedBlock = channel.map(READ_WRITE, blockId * blockSize, blockSize);
						random.nextBytes(block.array());
						mappedBlock.put(block);
						block.flip();

						md.update(block);
						block.flip();
					}
				} finally {
					final long t1 = nanoTime();
					System.out.println("Filled the 2G file in " + (t1  - t0) / 1000 / 1e3 + " ms.");
					digest = md.digest();
					System.out.println(toHexString(digest));
				}
			}

			@Nonnull
			@SuppressWarnings("null")
			final Path p = createTempFile(null, ".javafs");
			try (final FileSystem fs = FileSystem.create(p, 1024L * 1024 - 1)) {
				fs.getRoot().addChild(new File(file));

				final Directory root = fs.getRoot();
				System.out.println(root);
				final FileSystemEntry child = root.list().iterator().next();

				md.reset();
				md.update(child.getData());
				assertArrayEquals(digest, md.digest());
			}
		} finally {
			deleteFile(file);
		}
	}

	@Test
	@SuppressWarnings({ "static-method", "javadoc" })
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
						fs.getRoot().addChild(new File(file));
					}
					return CONTINUE;
				}
			});

			final Directory root = fs.getRoot();

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
			final long oldRootSize = root.getDataSize();

			assertEquals(oldFileCount, oldRootChildrenCount + 1);

			root.unlink(root.list().iterator().next().getName());

			final long newFileCount = fs.getFileCount();
			final int newRootChildrenCount = root.list().size();
			final long newFreeBlockCount = fs.getFreeBlockCount();
			final long newRootSize = root.getDataSize();

			assertEquals(newFileCount, newRootChildrenCount + 1);

			assertEquals(1, oldFileCount - newFileCount);
			assertEquals(1, oldRootChildrenCount - newRootChildrenCount);
			assertTrue(oldFreeBlockCount < newFreeBlockCount);
			assertTrue(oldRootSize > newRootSize);
		}
	}

	@Test
	@SuppressWarnings({ "static-method", "javadoc" })
	public void testFileSymlinkSupport() throws IOException {
		@Nonnull
		@SuppressWarnings("null")
		final Path tempFile = createTempFile(null, null);
		testSymlinkSupport(tempFile);
	}

	@Test
	@SuppressWarnings({ "static-method", "javadoc" })
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
				final SymbolicLink symlinkEntry0 = new SymbolicLink(link);
				/*
				 * Read the target of a detached entry.
				 */
				final Path target0 = symlinkEntry0.getTarget();
				assertTrue(symlinkEntry0.isDetached());

				fs.getRoot().addChild(symlinkEntry0);

				/*
				 * Re-read the root directory: make sure all values come from disk.
				 */
				final Directory root = fs.getRoot();
				System.out.println(root);

				final Set<FileSystemEntry> children = root.list();
				assertEquals(1, children.size());
				final SymbolicLink symlinkEntry1 = (SymbolicLink) children.iterator().next();

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
	@SuppressWarnings({ "static-method", "javadoc" })
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
			final Directory root = fs.getRoot();
			assertEquals("Empty directory should have a zero size", 0, root.getDataSize());

			final String name = newUniqueName(50);
			root.addChild(new Directory(name));

			/*
			 * Re-read the root directory: make sure all values come from disk.
			 */
			final FileSystemEntry root2 = fs.getRoot();
			assertEquals(fs.getBlockCount(0L), root2.getBlockCount());
			System.out.println(root2);
		}

		@Nonnull
		@SuppressWarnings("null")
		final Path p1 = createTempFile(null, ".javafs");
		try (final FileSystem fs = FileSystem.create(p1, 1024L * 1024 - 1)) {
			final Directory root = fs.getRoot();
			assertEquals("Empty directory should have a zero size", 0, root.getDataSize());

			final String name = newUniqueName(2 * fs.getBlockSize().getLength());
			root.addChild(new Directory(name));

			/*
			 * Re-read the root directory: make sure all values come from disk.
			 */
			final FileSystemEntry root2 = fs.getRoot();
			assertEquals(fs.getBlockCount(0L), root2.getBlockCount());
			System.out.println(root2);
		}
	}

	@Test
	@SuppressWarnings({ "static-method", "javadoc" })
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

	private static void deleteFile(final Path file) throws IOException {
		try {
			/*
			 * On Windows, the attempt to delete a file which was
			 * previously mmapped usually fails.
			 *
			 * For Sun JVM, it is possible to use the proprietary
			 * API as a workaround
			 * (see http://stackoverflow.com/questions/2972986):
			 *
			 * ((sun.nio.ch.DirectBuffer) buffer).cleaner().clean()
			 */
			delete(file);
		} catch (final AccessDeniedException ade) {
			getRuntime().addShutdownHook(new Thread(() -> {
				for (int i = 0; i < 100; i++) {
					try {
						delete(file);
						break;
					} catch (final AccessDeniedException ade2) {
						/*
						 * Calling gc() here is essential: otherwise,
						 * waiting for 10 seconds is not sufficient.
						 */
						gc();
						try {
							sleep(100);
						} catch (final InterruptedException ie) {
							/*
							 * Re-set the interrupted status.
							 */
							currentThread().interrupt();
						}
					} catch (final IOException ioe) {
						ioe.printStackTrace();
						break;
					}
				}
			}));
		}
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
