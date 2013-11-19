package ru.n5g.watchdir;

import java.nio.file.Path;


public interface FileChangeListener
{
  
	public void fileModified(Path file);
	
	public void fileCreated(Path file);
	
	public void fileDeleted(Path file);
	
}
