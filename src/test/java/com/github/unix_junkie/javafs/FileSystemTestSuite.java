/*-
 * $Id$
 */
package com.github.unix_junkie.javafs;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

/**
 * @author Andrew ``Bass'' Shcheglov &lt;mailto:andrewbass@gmail.com&gt;
 */
@RunWith(Suite.class)
@SuiteClasses({
	FileSystemTest.class,
	BlockSizeTest.class,
	FileSystemEntryTest.class,
	PosixAttributesTest.class,
	SizeUnitTest.class,
})
public final class FileSystemTestSuite {
	// empty
}
