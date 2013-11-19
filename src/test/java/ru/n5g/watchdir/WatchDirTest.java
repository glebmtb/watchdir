package ru.n5g.watchdir;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

/**
 * User: Belyaev Gleb
 * Date: 14.11.13
 */
public class WatchDirTest
{
    private final static String TEST_DIR = getTestFolder();
    private Path path = Paths.get(TEST_DIR + "test.txt");
    private WatchDir watchDir;
    private WatchDir.Listener listener = mock(WatchDir.Listener.class);

    private static String getTestFolder()
    {
        StringBuilder customTestPath = new StringBuilder();
        customTestPath.append(System.getProperty("user.home"));
        customTestPath.append(File.separator);
        customTestPath.append(WatchDirTest.class.getSimpleName());
        customTestPath.append(File.separator);
        return customTestPath.toString();
    }

    @Before
    public void setUpClass() throws Exception
    {
        File testDir = new File(TEST_DIR);
        if (testDir.exists())
        {
            FileUtils.forceDelete(testDir);
        }
        assertTrue(testDir.mkdirs());

        reset(listener);

        watchDir = new WatchDir(listener);
        watchDir.addPath(TEST_DIR);
    }

    @After
    public void tearDownClass() throws Exception
    {
        File testDir = new File(TEST_DIR);
        if (testDir.exists())
        {
            FileUtils.forceDelete(testDir);
        }
        assertFalse(testDir.exists());
    }

    @Test
    public void testWithRecursive() throws Exception
    {
        watchDir = new WatchDir(listener, true);
        watchDir.addPath(TEST_DIR);
        watchDir.start();

        FileUtils.touch(new File(path.toString()));
        verify(listener, timeout(100).atLeast(1)).doCreate(eq(path), any(Boolean.class));

        String underDir = TEST_DIR + "underDir" + File.separator;
        FileUtils.forceMkdir(new File(underDir));
        FileUtils.touch(new File(underDir + "file1.txt"));
        verify(listener, timeout(100).never()).doChange(eq(Paths.get(underDir + "file1.txt")), any(Boolean.class));
    }

    @Test
    public void testWithoutRecursive() throws Exception
    {
        watchDir = new WatchDir(listener, true);
        watchDir.addPath(TEST_DIR);
        watchDir.start();

        FileUtils.touch(new File(path.toString()));
        verify(listener, timeout(100).atLeast(1)).doCreate(eq(path), any(Boolean.class));

        String underDir = TEST_DIR + "underDir" + File.separator;
        FileUtils.forceMkdir(new File(underDir));
        FileUtils.touch(new File(underDir + "file1.txt"));
        verify(listener, timeout(100).never()).doChange(eq(Paths.get(underDir + "file1.txt")), any(Boolean.class));
    }

    /**
     * добавление новой папки
     *
     * @throws Exception
     */
    @Test
    public void testAddNewPath() throws Exception
    {
        watchDir.start();
        FileUtils.touch(new File(path.toString()));
        verify(listener, timeout(100).atLeast(1)).doCreate(eq(path), any(Boolean.class));

        StringBuilder customTestPath = new StringBuilder();
        customTestPath.append(System.getProperty("user.home"));
        customTestPath.append(File.separator);
        customTestPath.append(WatchDirTest.class.getSimpleName());
        customTestPath.append("_2");
        customTestPath.append(File.separator);

        String newPath = customTestPath.toString();
        File testDir = new File(newPath);
        if (testDir.exists())
        {
            FileUtils.forceDelete(testDir);
        }
        assertTrue(testDir.mkdirs());

        watchDir.addPath(newPath);
        String newFile = newPath + "testFile2.txt";
        FileUtils.touch(new File(newFile));
        verify(listener, timeout(100).atLeast(1)).doCreate(eq(Paths.get(newFile)), any(Boolean.class));

        FileUtils.forceDelete(testDir);
    }

    /**
     * проверить что работает перезапуск
     * <p/>
     * не реагирует на события
     * запуск
     * реагирует на события
     * остановка
     * не реагирует на события
     * запуск
     * реагирует на события
     *
     * @throws Exception
     */
    @Test
    public void testRestartMonitoring() throws Exception
    {
        FileUtils.touch(new File(path.toString()));
        verify(listener, timeout(100).never()).doChange(eq(path), any(Boolean.class));

        reset(listener);
        watchDir.start();

        FileUtils.forceDelete(new File(path.toString()));
        verify(listener, timeout(100).atLeast(1)).doChange(eq(path), any(Boolean.class));

        reset(listener);
        watchDir.stop();

        FileUtils.touch(new File(path.toString()));
        verify(listener, timeout(100).never()).doChange(eq(path), any(Boolean.class));

        reset(listener);
        watchDir.start();
//        Thread.sleep(100);

        FileUtils.forceDelete(new File(path.toString()));
        verify(listener, timeout(100).atLeast(1)).doChange(eq(path), any(Boolean.class));

        watchDir.stop();
    }

    /**
     * событие файла
     * проверить что события файла
     *
     * @throws Exception
     */
    @Test
    public void testChangeIsFile() throws Exception
    {
        watchDir.start();

        FileUtils.touch(new File(path.toString()));
        verify(listener, timeout(100).atLeast(1)).doCreate(eq(path), eq(false));
    }

    /**
     * событие каталога
     * проверить что событие каталога
     *
     * @throws Exception
     */
    @Test
    public void testChangeIsDirectory() throws Exception
    {
        watchDir.start();

        path = Paths.get(TEST_DIR + "testFolder");

        FileUtils.forceMkdir(new File(path.toString()));
        verify(listener, timeout(100).atLeast(1)).doCreate(eq(path), eq(true));
    }

    /**
     * проверка событие создания
     * проверить что было событие на создания
     *
     * @throws Exception
     */
    @Test
    public void testEventOnCreate() throws Exception
    {
        watchDir.start();

        FileUtils.touch(new File(path.toString()));
        verify(listener, timeout(100).atLeast(1)).doCreate(eq(path), any(Boolean.class));
    }

    /**
     * проверка событие изменения
     * проверить что было событие изменения
     *
     * @throws Exception
     */
    @Test
    public void testEventOnChange() throws Exception
    {
        FileUtils.touch(new File(path.toString()));

        watchDir.start();

        FileUtils.write(new File(path.toString()), "test");
        verify(listener, timeout(100).atLeast(1)).doChange(eq(path), any(Boolean.class));
    }

    /**
     * проверка событие удаление
     * проверить что было событие удаления
     *
     * @throws Exception
     */
    @Test
    public void testEventOnDelete() throws Exception
    {
        FileUtils.touch(new File(path.toString()));

        watchDir.start();
        Thread.sleep(100);
        FileUtils.forceDelete(new File(path.toString()));
        verify(listener, timeout(500).atLeast(1)).doDelete(eq(path), any(Boolean.class));
    }

    /**
     * Режим запуска, не срабатывали события а после запуска срабатывают
     *
     * @throws Exception
     */
    @Test
    public void testStartMonitoring() throws Exception
    {
        FileUtils.touch(new File(path.toString()));
        verify(listener, timeout(100).never()).doChange(any(Path.class), any(Boolean.class));

        watchDir.start();

        FileUtils.forceDelete(new File(path.toString()));
        verify(listener, timeout(100).atLeast(1)).doChange(eq(path), any(Boolean.class));

        watchDir.stop();
    }

    @Test
    public void testStopMonitoring() throws Exception
    {
        watchDir.start();

        FileUtils.touch(new File(path.toString()));
        verify(listener, timeout(100).atLeast(1)).doCreate(eq(path), any(Boolean.class));

        reset(listener);
        watchDir.stop();

        FileUtils.forceDelete(new File(path.toString()));
        verify(listener, timeout(100).never()).doChange(any(Path.class), any(Boolean.class));
    }

    @Test
    public void testEqPath() throws Exception
    {
        Path path1 = Paths.get(TEST_DIR + "test.txt");
        Path path2 = Paths.get(TEST_DIR + "test.txt");
        Path path3 = Paths.get(TEST_DIR);

        assertEquals(path1, path2);
        assertTrue(path1.equals(path2));

        assertNotEquals(path1, path3);
        assertFalse(path1.equals(path3));
    }
}
