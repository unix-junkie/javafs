/*-
 * $Id$
 */
package com.github.unix_junkie.javafs;

import static com.github.unix_junkie.javafs.FileType.FILE;
import static java.nio.file.Files.readAttributes;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardOpenOption.READ;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * <p>File system entry which corresponds to a regular file.</p>
 *
 * @author Andrew ``Bass'' Shcheglov &lt;mailto:andrewbass@gmail.com&gt;
 */
public final class File extends FileSystemEntry {
	public File(final Path source) throws IOException {
		super(source);

		this.dataSize = readAttributes(source, BasicFileAttributes.class, NOFOLLOW_LINKS).size();
	}

	public File(final PosixAttributes attributes,
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

	protected File(final PosixAttributes attributes,
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
	 * {@inheritDoc}
	 *
	 * @see FileSystemEntry#writeData()
	 */
	@Override
	protected void writeData() throws IOException {
		this.requireNotDetached();

		if (this.source == null) {
			return;
		}

		try (@Nonnull @SuppressWarnings("null")
				final FileChannel channel = FileChannel.open(this.source, READ)) {
			this.fileSystem.writeTo(this.firstBlockId, channel);
		}
	}

	/**
	 * {@inheritDoc}
	 *
	 * @see FileSystemEntry#getType()
	 */
	@Override
	protected FileType getType() {
		return FILE;
	}
}
