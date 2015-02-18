/*-
 * $Id$
 */
package com.github.unix_junkie.javafs;

import static com.github.unix_junkie.javafs.FileType.DIRECTORY;
import static com.github.unix_junkie.javafs.FileType.FILE;
import static com.github.unix_junkie.javafs.FileType.SYMBOLIC_LINK;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.lang.Math.min;
import static java.lang.String.format;
import static java.nio.file.Files.createSymbolicLink;
import static java.nio.file.Files.createTempFile;
import static java.nio.file.Files.delete;
import static java.nio.file.Files.getAttribute;
import static java.nio.file.Files.isDirectory;
import static java.nio.file.Files.isSymbolicLink;
import static java.nio.file.Files.readAttributes;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.Paths.get;
import static java.util.logging.Level.WARNING;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.file.Path;
import java.nio.file.attribute.UserPrincipal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Andrew ``Bass'' Shcheglov &lt;mailto:andrewbass@gmail.com&gt;
 */
public abstract class FileUtilities {
	@Nonnull
	@SuppressWarnings("null")
	private static final Logger LOGGER = Logger.getLogger(FileUtilities.class.getName());

	@Nullable
	private static volatile Boolean symbolicLinksSupported;

	@Nonnull
	private static final Object SYMBOLIC_LINKS_SUPPORTED_LOCK = new Object();

	private static final ConcurrentMap<Integer, String> USERS = new ConcurrentHashMap<>();

	private static final ConcurrentMap<Integer, String> GROUPS = new ConcurrentHashMap<>();

	private FileUtilities() {
		assert false;
	}

	public static short getUid(final Path path) {
		try {
			final Integer uid = (Integer) getAttribute(path, "unix:uid", NOFOLLOW_LINKS);
			USERS.putIfAbsent(uid, ((UserPrincipal) getAttribute(path, "posix:owner", NOFOLLOW_LINKS)).getName());
			return uid.shortValue();
		} catch (final UnsupportedOperationException ignored) {
			/*
			 * Windows.
			 */
			return 0;
		} catch (final IOException ioe) {
			LOGGER.log(WARNING, "", ioe);
			return 0;
		}
	}

	public static short getGid(final Path path) {
		try {
			final Integer gid = (Integer) getAttribute(path, "unix:gid", NOFOLLOW_LINKS);
			GROUPS.putIfAbsent(gid, ((UserPrincipal) getAttribute(path, "posix:group", NOFOLLOW_LINKS)).getName());
			return gid.shortValue();
		} catch (final UnsupportedOperationException ignored) {
			/*
			 * Windows.
			 */
			return 0;
		} catch (final IOException ioe) {
			LOGGER.log(WARNING, "", ioe);
			return 0;
		}
	}

	/**
	 * @param path the path whose attributes are to be read.
	 * @return the real (if possible) or fake POSIX attributes. Only the
	 *         least significant 12 bits of the returned short are used.
	 */
	public static PosixAttributes getPosixAttributes(final Path path) {
		return new PosixAttributes(path);
	}

	public static FileType getType(final Path path) {
		return isSymbolicLink(path) ? SYMBOLIC_LINK : isDirectory(path, NOFOLLOW_LINKS) ? DIRECTORY : FILE;
	}

	public static String uidToString(final short uid) {
		final String owner = USERS.get(Integer.valueOf(uid));
		@Nonnull
		@SuppressWarnings("null")
		final String s = owner == null ? Short.toString(uid) : owner;
		return s;
	}

	public static String gidToString(final short gid) {
		final String group = GROUPS.get(Integer.valueOf(gid));
		@Nonnull
		@SuppressWarnings("null")
		final String s = group == null ? Short.toString(gid) : group;
		return s;
	}

	public static byte getNlinks(final Path path) throws IOException {
		try {
			final Map<String, Object> attributes = readAttributes(path, "unix:*", NOFOLLOW_LINKS);
			final Integer nlinks = (Integer) attributes.get("nlinks");
			if (nlinks == null) {
				LOGGER.info(format("unix:nlinks unavailable for %s; returning 1", path));
				return 1;
			}
			return nlinks.byteValue();
		} catch (final UnsupportedOperationException uoe) {
			LOGGER.info(format("unix:nlinks unavailable for %s; returning 1", path));
			return 1;
		}
	}

	public static boolean symbolicLinksSupported() throws IOException {
		if (symbolicLinksSupported == null) {
			synchronized (SYMBOLIC_LINKS_SUPPORTED_LOCK) {
				if (symbolicLinksSupported == null) {
					@Nonnull
					@SuppressWarnings("null")
					final Path tempFile = createTempFile(null, null);
					try {
						final Path link = get(tempFile.getParent().toString(), tempFile.getFileName() + ".lnk");
						try {
							createSymbolicLink(link, tempFile);
							delete(link);
							symbolicLinksSupported = TRUE;
						} catch (final UnsupportedOperationException uoe) {
							symbolicLinksSupported = FALSE;
						}
					} finally {
						delete(tempFile);
					}
				}
			}
		}

		assert symbolicLinksSupported != null;

		return symbolicLinksSupported.booleanValue();
	}

	/**
	 * <p>Returns how many blocks of size {@code blockSize} will be required
	 * to store data of length {@code dataLength}.</p>
	 *
	 * @param dataLength the length of the data chunk.
	 * @param blockSize the block size.
	 * @return how many blocks of size {@code blockSize} will be required to
	 *         store data of length {@code dataLength}.
	 */
	static long getBlockCount(final long dataLength, final long blockSize) {
		if (blockSize <= 0) {
			throw new IllegalArgumentException(String.valueOf(blockSize));
		}

		if (dataLength == 0) {
			/*
			 * Even an empty file will occupy a block on the file system.
			 */
			return 1;
		}

		final long blockCount = dataLength / blockSize;
		return dataLength % blockSize == 0
				? blockCount
				: blockCount + 1;
	}

	/**
	 * <p>Writes file contents to the previously allocated blocks.</p>
	 *
	 * @param source the buffer which contains file contents.
	 * @param destinations the blocks to write file contents to.
	 * @param <T> the actual {@link ByteBuffer} descendant, e.g.: {@link
	 *        MappedByteBuffer}.
	 */
	static <T extends ByteBuffer> void writeTo(final ByteBuffer source, final List<T> destinations) {
		writeTo(source, destinations, 0L);
	}

	/**
	 * <p>Appends file contents to the previously allocated blocks, starting
	 * at {@code destinationOffset}.</p>
	 *
	 * @param source the buffer which contains file contents.
	 * @param destinations the blocks to write file contents to.
	 * @param destinationOffset the offset to start writing at.
	 * @param <T> the actual {@link ByteBuffer} descendant, e.g.: {@link
	 *        MappedByteBuffer}.
	 */
	static <T extends ByteBuffer> void writeTo(final ByteBuffer source, final List<T> destinations, final long destinationOffset) {
		final int originalLimit = source.limit();
		final int position = source.position();
		if (originalLimit > 0 && originalLimit == position) {
			throw new IllegalArgumentException(format("source not flipped: position = %d; limit = %d",
					Integer.valueOf(position),
					Integer.valueOf(originalLimit)));
		}

		long bytesToSkip = destinationOffset;
		for (final ByteBuffer destination : destinations) {
			/*
			 * Write at most "blockSize" bytes to each block.
			 */
			final int blockSize = destination.limit();

			if (bytesToSkip > blockSize) {
				bytesToSkip -= blockSize;
				continue;
			}

			assert bytesToSkip <= Integer.MAX_VALUE;
			destination.position((int) bytesToSkip);

			source.limit(min(source.position() + blockSize - (int) bytesToSkip, originalLimit));
			destination.put(source);

			bytesToSkip = 0;
		}
	}
}
