/*-
 * $Id$
 */
package com.github.unix_junkie.javafs;

import static com.github.unix_junkie.javafs.SizeUnit.parseSize;
import static java.lang.String.format;
import static java.lang.System.getProperty;
import static java.nio.file.Paths.get;
import static java.util.Arrays.asList;
import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;
import static java.util.stream.Collectors.joining;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

/**
 * @author Andrew ``Bass'' Shcheglov &lt;mailto:andrewbass@gmail.com&gt;
 */
abstract class Main {
	static {
		try {
			LogManager.getLogManager().readConfiguration(Main.class.getResourceAsStream("/logging.properties"));
		} catch (final IOException ioe) {
			System.err.println(ioe.getMessage());
		}
	}

	@Nonnull
	@SuppressWarnings("null")
	private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

	@Nonnull
	private static final String COMMANDS[] = {
		"mkfs",
	};

	private Main() {
		assert false;
	}

	private static int mkfs(final String ... args) throws IOException {
		if (args.length == 1 && args[0].equals("help")) {
			usageMkfs(0);
			return 0;
		} else if (args.length == 3 && args[0].equals("-l")) {
			final Pattern pattern = Pattern.compile("(\\d+)%FREE");
			final Matcher matcher = pattern.matcher(args[1]);
			final Path path = Paths.get(args[2]);

			final long length;
			final Path parent = path.getParent();
			final long usableSpace = Files.getFileStore(parent != null ? parent : get(getProperty("user.dir", "."))).getUsableSpace();
			if (matcher.matches()) {
				final long pFree = Long.parseLong(matcher.group(1));
				if (0 >= pFree || pFree > 100) {
					throw new IllegalArgumentException(format("Invalid free size percentage: %d%%FREE", Long.valueOf(pFree)));
				}
				length = usableSpace * pFree / 100;
			} else {
				length = parseSize(args[1]);
				if (length > usableSpace) {
					throw new IOException(format("Not enough free space: %s requested, %s available", SizeUnit.toString(length), SizeUnit.toString(usableSpace)));
				}
			}
			FileSystem.create(path, length);
			return 0;
		} else {
			usageMkfs(1);
			return 1;
		}
	}

	private static void usageMkfs(final int status) {
		final String className = Main.class.getName();

		System.err.println("Usage:");
		System.err.println(format("\t%s mkfs help", className));
		System.err.println(format("\t%s mkfs -l <size>[bBkKmMgGtTpPeE|%%FREE] <file>", className));
		System.exit(status);
	}

	private static void usage(final int status) {
		final String className = Main.class.getName();

		System.err.println("Usage:");
		System.err.println(format("\t%s -h|--help", className));
		System.err.println(format("\t%s <%s> help", className, asList(COMMANDS).stream().collect(joining("|"))));
		System.err.println(format("\t%s <%s> [options]", className, asList(COMMANDS).stream().collect(joining("|"))));
		System.exit(status);
	}

	public static void main(final String[] args) {
		if (args.length == 0) {
			usage(1);
			return;
		}

		switch (args[0]) {
		case "mkfs":
			try {
				final ArrayList<String> commandArgs0 = new ArrayList<>(asList(args));
				commandArgs0.remove(0);
				@Nonnull
				@SuppressWarnings("null")
				final String commandArgs1[] = commandArgs0.toArray(new String[0]);
				System.exit(mkfs(commandArgs1));
			} catch (final IllegalArgumentException iae) {
				System.err.println(iae.getMessage());
				LOGGER.log(WARNING, "", iae);
				System.exit(1);
			} catch (final IOException ioe) {
				System.err.println(ioe.getMessage());
				LOGGER.log(SEVERE, "", ioe);
				System.exit(1);
			}
			break;
		case "-h":
		case "--help":
		case "-?":
		case "/?":
			usage(0);
			break;
		default:
			usage(1);
			break;
		}
	}
}
