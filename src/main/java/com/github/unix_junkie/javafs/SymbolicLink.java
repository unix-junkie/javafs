/*-
 * $Id$
 */
package com.github.unix_junkie.javafs;

import static com.github.unix_junkie.javafs.FileType.SYMBOLIC_LINK;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Date;

import javax.annotation.Nullable;

/**
 * @author Andrew ``Bass'' Shcheglov &lt;mailto:andrewbass@gmail.com&gt;
 */
public final class SymbolicLink extends FileSystemEntry {
	public SymbolicLink(final Path source) throws IOException {
		super(source);
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
		super(SYMBOLIC_LINK, attributes, numberOfLinks, uid, gid, size,
				creationTime,
				modificationTime,
				accessTime, name, encodedName);
	}
}
