/*-
 * $Id$
 */
package com.github.unix_junkie.javafs;

import static com.github.unix_junkie.javafs.FileType.DIRECTORY;
import static com.github.unix_junkie.javafs.FileType.FILE;
import static com.github.unix_junkie.javafs.FileType.SYMBOLIC_LINK;
import static java.nio.file.Files.getAttribute;
import static java.nio.file.Files.isDirectory;
import static java.nio.file.Files.isSymbolicLink;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.util.logging.Level.WARNING;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

/**
 * @author Andrew ``Bass'' Shcheglov &lt;mailto:andrewbass@gmail.com&gt;
 */
public abstract class FileUtilities {
	private static final Logger LOGGER = Logger.getLogger(FileUtilities.class.getName());

	private FileUtilities() {
		assert false;
	}

	public static short getUid(@Nonnull final Path path) {
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

	public static short getGid(@Nonnull final Path path) {
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
	public static PosixAttributes getPosixAttributes(@Nonnull final Path path) {
		return new PosixAttributes(path);
	}

	public static FileType getType(final Path path) {
		return isSymbolicLink(path) ? SYMBOLIC_LINK : isDirectory(path, NOFOLLOW_LINKS) ? DIRECTORY : FILE;
	}

	public static String uidToString(final short uid) {
		return Short.toString(uid);
	}

	public static String gidToString(final short gid) {
		return Short.toString(gid);
	}
}
