/*-
 * $Id$
 */
package com.github.unix_junkie.javafs;

import static com.github.unix_junkie.javafs.FileType.DIRECTORY;
import static com.github.unix_junkie.javafs.FileType.FILE;
import static com.github.unix_junkie.javafs.FileType.SYMBOLIC_LINK;
import static java.lang.String.format;
import static java.nio.file.Files.getAttribute;
import static java.nio.file.Files.isDirectory;
import static java.nio.file.Files.isSymbolicLink;
import static java.nio.file.Files.readAttributes;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.util.logging.Level.WARNING;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

/**
 * @author Andrew ``Bass'' Shcheglov &lt;mailto:andrewbass@gmail.com&gt;
 */
public abstract class FileUtilities {
	@Nonnull
	@SuppressWarnings("null")
	private static final Logger LOGGER = Logger.getLogger(FileUtilities.class.getName());

	private FileUtilities() {
		assert false;
	}

	public static short getUid(final Path path) {
		try {
			final Integer uid = (Integer) getAttribute(path, "unix:uid", NOFOLLOW_LINKS);
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
		@Nonnull
		@SuppressWarnings("null")
		final String s = Short.toString(uid);
		return s;
	}

	public static String gidToString(final short gid) {
		@Nonnull
		@SuppressWarnings("null")
		final String s = Short.toString(gid);
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
}
