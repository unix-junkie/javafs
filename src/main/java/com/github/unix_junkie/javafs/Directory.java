/*-
 * $Id$
 */
package com.github.unix_junkie.javafs;

import static com.github.unix_junkie.javafs.FileType.DIRECTORY;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Date;

import javax.annotation.Nullable;

/**
 * @author Andrew ``Bass'' Shcheglov &lt;mailto:andrewbass@gmail.com&gt;
 */
public final class Directory extends FileSystemEntry {
	public Directory(final String name) {
		this(name, new Date());
	}

	public Directory(final Path source) throws IOException {
		super(source);
	}

	public Directory(final PosixAttributes attributes,
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

	protected Directory(final PosixAttributes attributes,
			final byte numberOfLinks,
			final short uid,
			final short gid,
			final long size,
			final Date creationTime,
			final Date modificationTime,
			final Date accessTime,
			final String name,
			@Nullable final ByteBuffer encodedName) {
		super(DIRECTORY, attributes, numberOfLinks, uid, gid, size,
				creationTime,
				modificationTime,
				accessTime, name, encodedName);
	}

	private Directory(final String name, final Date creationTime) {
		this(new PosixAttributes((short) 0755), (byte) 1,
				(short) 0, (short) 0, 0L, creationTime,
				creationTime, creationTime, name);
	}
}
