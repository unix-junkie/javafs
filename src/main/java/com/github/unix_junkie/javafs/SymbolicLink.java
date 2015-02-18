/*-
 * $Id$
 */
package com.github.unix_junkie.javafs;

import static com.github.unix_junkie.javafs.FileType.SYMBOLIC_LINK;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readAttributes;
import static java.nio.file.Files.readSymbolicLink;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.Paths.get;
import static java.util.logging.Level.WARNING;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * <p>File system entry which corresponds to a symbolic link.</p>
 *
 * @author Andrew ``Bass'' Shcheglov &lt;mailto:andrewbass@gmail.com&gt;
 */
public final class SymbolicLink extends FileSystemEntry {
	@Nonnull
	@SuppressWarnings("null")
	private static final Logger LOGGER = Logger.getLogger(SymbolicLink.class.getName());

	public SymbolicLink(final Path source) throws IOException {
		super(source);

		/*
		 * Windows-specific: symbolic link size reported is 0.
		 */
		final long reportedSize = readAttributes(source, BasicFileAttributes.class, NOFOLLOW_LINKS).size();
		final String targetPath = readSymbolicLink(source).toString();
		final ByteBuffer targetPathBytes = UTF_8.newEncoder().encode(CharBuffer.wrap(targetPath));
		final int targetPathLength = targetPathBytes.limit() - targetPathBytes.position();
		assert reportedSize == 0 || reportedSize == targetPathLength : format("For %s: reported size = %d; target path length = %d", targetPath,
				Long.valueOf(reportedSize),
				Integer.valueOf(targetPathLength));
		this.dataSize = reportedSize != 0 ? reportedSize : targetPathLength;
	}

	public SymbolicLink(final PosixAttributes attributes,
			final byte numberOfLinks,
			final short uid,
			final short gid,
			final long size,
			final Date creationTime,
			final Date modificationTime,
			final Date accessTime,
			final String name) {
		this(attributes, numberOfLinks, uid, gid, size,
				creationTime,
				modificationTime,
				accessTime, name, null);
	}

	protected SymbolicLink(final PosixAttributes attributes,
			final byte numberOfLinks,
			final short uid,
			final short gid,
			final long size,
			final Date creationTime,
			final Date modificationTime,
			final Date accessTime,
			final String name,
			@Nullable final ByteBuffer encodedName) {
		super(attributes, numberOfLinks, uid, gid, size,
				creationTime,
				modificationTime,
				accessTime, name, encodedName);
	}

	/**
	 * <p>Returns this symbolic link's target. This method works for both
	 * attached and detached entries.</p>
	 *
	 * @return this symbolic link's target.
	 * @throws IOException if an I/O error occurs.
	 */
	@Nullable
	@CheckForNull
	public Path getTarget() throws IOException {
		if (this.isDetached()) {
			return this.source == null ? null : readSymbolicLink(this.source);
		}

		/*
		 * Only applicable to symbolic links under 2G.
		 */
		return get(UTF_8.newDecoder().decode(this.getData()).toString());
	}

	/**
	 * {@inheritDoc}
	 *
	 * @see Object#toString()
	 */
	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append(super.toString());
		builder.append(" -> ");
		try {
			builder.append(this.getTarget());
		} catch (final IOException ioe) {
			LOGGER.log(WARNING, "", ioe);
			builder.append(ioe.getMessage());
		}
		@Nonnull
		@SuppressWarnings("null")
		final String s = builder.toString();
		return s;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @see FileSystemEntry#writeData()
	 */
	@Override
	protected void writeData() throws CharacterCodingException, IOException {
		this.requireNotDetached();

		if (this.source == null) {
			return;
		}

		/*
		 * The buffer already has its position set to 0,
		 * so there's no need in flipping.
		 */
		@Nonnull
		@SuppressWarnings("null")
		final ByteBuffer contents = UTF_8.newEncoder().encode(CharBuffer.wrap(readSymbolicLink(this.source).toString()));
		this.fileSystem.writeTo(this.firstBlockId, contents);
	}

	/**
	 * {@inheritDoc}
	 *
	 * @see FileSystemEntry#getType()
	 */
	@Override
	protected FileType getType() {
		return SYMBOLIC_LINK;
	}
}
