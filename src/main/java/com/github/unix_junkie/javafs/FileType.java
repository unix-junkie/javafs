/*-
 * $Id$
 */
package com.github.unix_junkie.javafs;

/**
 * @author Andrew ``Bass'' Shcheglov &lt;mailto:andrewbass@gmail.com&gt;
 */
public enum FileType {
	DIRECTORY('d'),
	FILE('-'),
	SYMBOLIC_LINK('l'),
	;

	private final char type;

	private FileType(final char type) {
		this.type = type;
	}

	public char getType() {
		return this.type;
	}
}
