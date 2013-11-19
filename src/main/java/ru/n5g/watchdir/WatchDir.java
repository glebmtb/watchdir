package ru.n5g.watchdir;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * User: Belyaev Gleb
 * Date: 14.11.13
 */
public class WatchDir
{
    /**
     * Слушатель, для оповещения об изменениях
     */
    private final FileChangeListener listener;
    /**
     * Флаг используется для определения использовать рекурсивное добавление файлов.
     */
    private boolean isRecursive;
    private final List<Path> pathList;
    private final List<Path> regList;
    private boolean isRun=false;

    private Logger logger = LoggerFactory.getLogger(getClass().getName());
    /**
     * Флаг используется для логирования добавления новых папок для мониторинга
     */
    private boolean isTrace = false;


    public WatchDir(FileChangeListener listener, boolean isRecursive)
    {
        this.listener = listener;
        this.pathList = new ArrayList<>();
        this.regList = new ArrayList<>();
        this.isRecursive = isRecursive;
    }


    /**
     * По умолчанию рекурсивное добавление отключено
     */
    public WatchDir(FileChangeListener listener)
    {
        this(listener, true);
    }

    public void start()
    {
        // включить трассировку после старта программы (уведомление о новых папках)
        this.isTrace = true;

        for (Path path : pathList)
        {
            registerPath(path);
        }
        isRun = true;
    }

    public void stop()
    {
        isRun = false;
        FileChangeMonitor.unregister(listener);
    }

    public void addPath(Path path)
    {
        pathList.add(path);
        if (isRun)
        {
            registerPath(path);
        }
    }

    public void addPath(String path)
    {
        addPath(Paths.get(path));
    }

    private void registerPath(Path path)
    {
        try
        {

            if (isRecursive)
            {
                logger.debug("Сканирование {} ...", path);
                registerAll(path);
                logger.debug("Сканирование законченно.");
            }
            else
            {
                registerInWatchService(path);
            }

        }
        catch (IOException e)
        {
            logger.error("Ошибка добавление папки для мониторинга", e);
        }
    }

    /**
     * Регистрация папки в WatchService
     */
    private void registerInWatchService(Path path) throws IOException
    {
        regList.add(path);
        FileChangeMonitor.register(listener, path);
    }

    /**
     * Регистрация папки и всех вложенных каталогов в WatchService.
     */
    private void registerAll(Path start) throws IOException
    {
        // регистрация папки и вложенных папок
        Files.walkFileTree(start, new SimpleFileVisitor<Path>()
        {
            @Override
            public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attrs)
                    throws IOException
            {
                registerInWatchService(path);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
