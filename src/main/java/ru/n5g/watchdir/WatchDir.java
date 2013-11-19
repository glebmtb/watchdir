package ru.n5g.watchdir;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.*;

/**
 * User: Belyaev Gleb
 * Date: 14.11.13
 */
public class WatchDir
{
    private final WatchService watcher;
    /**
     * Слушатель, для оповещения об изменениях
     */
    private volatile Listener listener;
    /**
     * Флаг используется для определения использовать рекурсивное добавление файлов.
     */
    private final boolean isRecursive;
    private final List<Path> pathList;
    /**
     * Ключи которые будут сигналом
     */
    private volatile Map<WatchKey, Path> keys;
    private Monitoring monitoring;
    private Logger logger = LoggerFactory.getLogger(getClass().getName());
    /**
     * Флаг используется для логирования добавления новых папок для мониторинга
     */
    private boolean isTrace = false;


    public WatchDir(Listener listener, boolean isRecursive)
    {
        keys = new HashMap<>();
        this.listener = listener;
        this.pathList = new ArrayList<>();
        this.isRecursive = isRecursive;
        try
        {
            this.watcher = FileSystems.getDefault().newWatchService();
        }
        catch (IOException e)
        {
            logger.error("error initialization WatchDir", e);
            throw new RuntimeException();
        }
    }

    /**
     * По умолчанию рекурсивное добавление отключено
     */
    public WatchDir(Listener listener)
    {
        this(listener, false);
    }

    public void start()
    {
        // включить трассировку после старта программы (уведомление о новых папках)
        this.isTrace = true;

        if (monitoring == null)
        {
            monitoring = new Monitoring();
            new Thread(monitoring).start();
        }

        for (Path path : pathList)
        {
            registerPath(path);
        }
    }

    public void stop()
    {
        monitoring.stop();
        monitoring = null;

        for (WatchKey key : keys.keySet())
        {
            key.cancel();
        }
        keys.clear();
    }

    public void addPath(Path path)
    {
        pathList.add(path);
        if (monitoring != null)
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
        WatchKey key = path.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        if (isTrace)
        {
            Path prev = keys.get(key);
            if (prev == null)
            {
                logger.debug("Добавлена папка для мониторинга: {}", path);
            }
            else
            {
                if (!path.equals(prev))
                {
                    logger.debug("Обновлена папка: {} -> {}", prev, path);
                }
            }
        }
        keys.put(key, path);
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


    public interface Listener
    {
        /**
         * создание файла
         */
        void doCreate(Path path, Boolean isDirectory);

        /**
         * изменение файла
         */
        void doChange(Path path, Boolean isDirectory);


        /**
         * удаление файла
         */
        void doDelete(Path path, Boolean isDirectory);
    }

    public class ListenerAdapter implements Listener{

        @Override
        public void doCreate(Path path, Boolean isDirectory)
        {

        }

        @Override
        public void doChange(Path path, Boolean isDirectory)
        {

        }

        @Override
        public void doDelete(Path path, Boolean isDirectory)
        {

        }
    }

    private class Monitoring implements Runnable
    {
        private volatile boolean isRun = true;

        @Override
        public void run()
        {
            logger.debug("мониторинг запустился");
            while (true)
            {
                WatchKey key;
                try
                {
                    key = watcher.take();
                }
                catch (InterruptedException x)
                {
                    logger.error("Ошибка получения ключа событий", x);
                    continue;
                }
                Path dir = keys.get(key);
                if (!isRun && dir == null)
                {
                    break;
                }

                for (WatchEvent<?> event : key.pollEvents())
                {
                    WatchEvent.Kind kind = event.kind();
                    // Получение информации об изменившимся файле или каталоге
                    WatchEvent<Path> ev = cast(event);
                    Path name = ev.context();
                    Path fullName = dir.resolve(name);
                    Boolean isDirectory = Files.isDirectory(fullName, NOFOLLOW_LINKS);

                    logger.debug(
                            "Изменение: name: {}, fullName: {}, isFile: {}, isDirectory: {},  eventType: {}, event.kind().name(): {}",
                            name.toString(), fullName.toString(), isDirectory, "null", event.kind().name());

                    // событие
                    if (kind == ENTRY_DELETE)
                    {
                        listener.doDelete(fullName, isDirectory);
                    }
                    else if (kind == ENTRY_CREATE)
                    {
                        listener.doCreate(fullName, isDirectory);
                    }
                    else if (kind == ENTRY_MODIFY)
                    {
                        listener.doChange(fullName, isDirectory);
                    }


                    // Если создалась новая папка, и включена рекурсия, то
                    // регистрируем ее и ее под папки
                    if (isRecursive && (kind == ENTRY_CREATE))
                    {
                        try
                        {
                            if (isDirectory)
                            {
                                registerAll(fullName);
                            }
                            else
                            {
                                logger.debug("Добавился новый файл: {}", name);
                            }
                        }
                        catch (IOException x)
                        {
                            logger.error("Ошибка чтения файла", x);
                        }
                    }
                }
            }
            logger.debug("мониторинг остановился");
        }

        public void stop()
        {
            isRun = false;
        }

        @SuppressWarnings ("unchecked")
        private <T> WatchEvent<T> cast(WatchEvent<?> event)
        {
            return (WatchEvent<T>)event;
        }
    }
}
