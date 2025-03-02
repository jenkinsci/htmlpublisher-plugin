package htmlpublisher.util;

import hudson.util.FileVisitor;

import java.io.File;
import java.io.IOException;

class IOExceptionFileVisitor extends FileVisitor {
	public void visit(File f, String relativePath) throws IOException {
		throw new IOException("Simulated IO Error");
	}
}