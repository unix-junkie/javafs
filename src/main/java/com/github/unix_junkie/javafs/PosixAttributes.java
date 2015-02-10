/*-
 * $Id$
 */
package com.github.unix_junkie.javafs;

import static java.nio.file.Files.isDirectory;
import static java.nio.file.Files.isSymbolicLink;
import static java.nio.file.Files.readAttributes;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.util.logging.Level.WARNING;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

/**
 * @author Andrew ``Bass'' Shcheglov &lt;mailto:andrewbass@gmail.com&gt;
 */
public final class PosixAttributes {
	private static final Logger LOGGER = Logger.getLogger(PosixAttributes.class.getName());

	private final short value;

	public PosixAttributes(@Nonnull final Path path) {
		this.value = extractValue(path);
	}

	public PosixAttributes(final short value) {
		this.value = value;
	}

	/**
	 * @return the real (if possible) or fake POSIX attributes. Only the
	 *         least significant 12 bits of the returned short are used.
	 */
	public short getValue() {
		return this.value;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @see Object#toString()
	 */
	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();

		final char permissions[] = {'r', 'w', 'x', 'r', 'w', 'x', 'r', 'w', 'x'};
		for (int i = 0, n = permissions.length; i < n; i++) {
			if ((this.value & 1 << n - i - 1) == 0) {
				permissions[i] = '-';
			}
		}
		if ((this.value >> 11 & 1) == 1) {
			permissions[2] = 's';
		}
		if ((this.value >> 10 & 1) == 1) {
			permissions[5] = 's';
		}
		if ((this.value >> 9 & 1) == 1) {
			permissions[8] = 't';
		}

		return builder.append(permissions).toString();
	}

	private static short extractValue(final Path path) {
		try {
			final Set<PosixFilePermission> permissions = readAttributes(path, PosixFileAttributes.class, NOFOLLOW_LINKS).permissions();
			short posixAttributes = 0;
			final PosixFilePermission[] values = PosixFilePermission.values();
			for (final PosixFilePermission permission : values) {
				if (permissions.contains(permission)) {
					posixAttributes |= 1 << values.length - permission.ordinal() - 1;
				}
			}
			return posixAttributes;
		} catch (final UnsupportedOperationException uoe) {
			/*
			 * Windows.
			 */
			return (short) (isDirectory(path, NOFOLLOW_LINKS) ? 0755 : 0744);
		} catch (final IOException ioe) {
			LOGGER.log(WARNING, "", ioe);
			return (short) (isSymbolicLink(path) ? 0777 : isDirectory(path, NOFOLLOW_LINKS) ? 0755 : 0744);
		}
	}
}
