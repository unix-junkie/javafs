/*-
 * $Id$
 */
package com.github.unix_junkie.javafs;

import static com.github.unix_junkie.javafs.FileType.DIRECTORY;
import static com.github.unix_junkie.javafs.FileType.FILE;
import static com.github.unix_junkie.javafs.FileType.SYMBOLIC_LINK;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
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
import java.nio.file.Path;
import java.nio.file.attribute.UserPrincipal;
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
		final Map<String, Object> attributes = readAttributes(path, "unix:*", NOFOLLOW_LINKS);
		final Integer nlinks = (Integer) attributes.get("nlinks");
		if (nlinks == null) {
			LOGGER.info(format("unix:nlinks unavailable for %s; returning 1", path));
			return 1;
		}
		return nlinks.byteValue();
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
}
