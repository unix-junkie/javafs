/*-
 * $Id$
 */
package com.github.unix_junkie.javafs;

/**
 * <p>A {@code FileType} represents file types supported by this file system
 * driver.</p>
 *
 * @author Andrew ``Bass'' Shcheglov &lt;mailto:andrewbass@gmail.com&gt;
 */
public enum FileType {
	/**
	 * A directory, see {@link Directory}. 
	 */
	DIRECTORY('d'),
	/**
	 * A regular file, see {@link File}.
	 */
	FILE('-'),
	/**
	 * A symbolic link, see {@link SymbolicLink}.
	 */
	SYMBOLIC_LINK('l'),
	;

	private final char type;

	private FileType(final char type) {
		this.type = type;
	}

	/**
	 * <p>Returns the character which ``ls'' utility uses to denote file type
	 * (``-'' for regular files, ``d'' for directories and ``l'' for symbolic
	 * links).</p>
	 *
	 * @return the character which ``ls'' utility uses to denote file type
	 *         (``-'' for regular files, ``d'' for directories and ``l'' for
	 *         symbolic links).
	 */
	public char getType() {
		return this.type;
	}
}
