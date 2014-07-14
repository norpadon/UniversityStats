package aggregators

import jxl.*
import java.net.*
import java.util.ArrayList
import java.util.regex.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.TreeMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.Comparator

// Страница со списком направлений подготовки.
private val programsPage = BufferedReader(InputStreamReader(
        (URL("http://ba.hse.ru/base2014").openConnection() as HttpURLConnection).getInputStream()!!)).readText()
// Страница с информацией о количестве мест.
private val placesPage = BufferedReader(InputStreamReader(
        (URL("http://ba.hse.ru/kolmest2014").openConnection() as HttpURLConnection).getInputStream()!!)).readText()

// Отображение (направление подготовки -> количество бюджетных мест).
private val placesMap = TreeMap<String, Int>()
// Отображение (направление подготовки -> квота целевиков).
private val targetedMap = TreeMap<String, Int>()
// Отображение (направление подготовки -> квота льготников).
private val exemptsMap = TreeMap<String, Int>()

// Пул потоков для асинхронного получения и обработки данных.
private val threadPool = Executors.newCachedThreadPool()

// Функция, облегчающая перевод строк в числа
private fun parseIfNotEmpty(string: String): Int {
    return if (string != "") Integer.parseInt(string) else 0
}

/*
 * Обобщенный агрегатор для Высшей Школы Экономики.
 * Конструктор принимает адрес excel таблицы с данными
 */
class HseAggregator(programUrl : String) : Aggregator {
    // Получение  таблицы
    private val connection = URL(programUrl).openConnection() as HttpURLConnection
    private val dataStream = connection.getInputStream()
    private val spreadsheet = Workbook.getWorkbook(dataStream)!!.getSheet(0)!!
    {
        // Заполнение таблицы мест в случае, если она еще не заполнена.
        if (placesMap.size == 0) {
            loadPlacesCount()
        }
    }

    // Вспомогательная функция для получнения содержимого ячейки таблицы
    private fun cell(column : Int, row : Int) : String {
        return spreadsheet.getCell(column, row)!!.getContents()!!
    }

    // Название направления подготовки получаем из кавычек внутри ячейки A2.
    override val name = cell(0, 1).split("\"")[1]

    // Получаем список абитуриентов.
    private val dataList = ArrayList<Record>();
    {
        // Если на направление нужно сдавать 3 экзамена, в ячейке L5 находится строка "Сумма баллов".
        val subjectsCount = if (cell(10, 4) == "Сумма баллов") 3 else 4
        // Таблица начинается с седьмой строки.
        var currentRow = 6
        // Заполняем таблицу пока можем.
        while (currentRow < spreadsheet.getRows() && cell(2, currentRow) != "") {
            // Имя.
            val name = cell(2, currentRow)
            // Буфер для хранения суммарного балла.
            var score = 0
            // Считаем суммарный балл.
            for (i in 7..6 + subjectsCount) {
                score += parseIfNotEmpty(cell(i, currentRow))
            }
            // Считаем приоритет.
            val priority = if (cell(3, currentRow) == "Подлинник") 0 else 1
            // Смотрим на олимпиады.
            val isOlymp = cell(4, currentRow) != ""
            // Смотрим на наличие льгот и целевого зачисления
            val isExempt = cell(5, currentRow) == "+"
            val isTargeted = cell(6, currentRow) == "+"
            // Сохраням полученные данные.
            dataList.add(Record(name, score, isOlymp, priority, isTargeted, isExempt))
            ++currentRow
        }
    }

    //Тут все ясно.
    override val placesCount = placesMap[name]!!
    override val exempts = exemptsMap[name]!!
    override val targeted = targetedMap[name]!!
    override val reserve = placesCount / 4

    override fun getRecords() : List<Record> {
        return ArrayList<Record>(dataList)
    }
}

// Треш, угар и содомия.
private fun loadPlacesCount() {
    val regex = Pattern.compile(
            """<td>\s*<a href="[^<>]*">([^<>]*)</a>((<br>)|[^<>])*</td>""" +
            """\s*<td style="[^<>]*">(&nbsp;)?(\d*)</td>""" +
            """\s*<td style="[^<>]*">(&nbsp;)?(\d*)</td>""" +
            """\s*<td style="[^<>]*">(&nbsp;)?(\d*)</td>"""
    )

    try {
        val matcher = regex.matcher(placesPage)
        while (matcher.find()) {
            val name = matcher.group(1)!!
            placesMap.set(name, parseIfNotEmpty(matcher.group(5)!!));
            targetedMap.set(name, parseIfNotEmpty(matcher.group(7)!!));
            exemptsMap.set(name, parseIfNotEmpty(matcher.group(9)!!))
        }
    } catch (e: Exception) {
        throw Exception("Ошибка на странице с количеством мест!")
    }

    placesMap.set("Программа двух дипломов по экономике НИУ ВШЭ и Лондонского университета", 0)
    targetedMap.set("Программа двух дипломов по экономике НИУ ВШЭ и Лондонского университета", 0)
    exemptsMap.set("Программа двух дипломов по экономике НИУ ВШЭ и Лондонского университета", 0)
}

// Возвращает аггрегаторы для всех направлений подготовки ВШЭ.
private fun getAllHseAggregators() : List<HseAggregator> {

    // Страшное регулярное выражение для поиска ссылок на excel файлы.
    val regex = Pattern.compile("""href="([^"]*.xls)"""")
    val matcher = regex.matcher(programsPage)

    // Список задач, завершения котрых нажно дождаться.
    val tasks = ArrayList<Future<HseAggregator>>()
    //Ищем ссылки.
    while (matcher.find()) {
        val link = matcher.group(1)!!
        // И просим наш пул потоков их обработать.
        tasks.add(threadPool.submit<HseAggregator> {
            HseAggregator(link)
        })
    }

    // Как ни странно, результат.
    val result = ArrayList<HseAggregator>(tasks.size);
    // Ждем завершения обработки.
    for (task in tasks) {
        result.add(task.get()!!)
    }

    // Сортируем массив по названиям направллений (для удобства).
    result sort object : Comparator<HseAggregator> {
        override fun compare(a: HseAggregator, b: HseAggregator): Int = a.name compareToIgnoreCase b.name
    }

    // Возвращаем резульат.
    return result
}

// Печатает статистику.
fun printData() {
    for (hse in getAllHseAggregators()) {
        val firstWave = hse.getMaxPassingScore()
        val minSecondWave = hse.getMinPassingScore()
        val meanSecondWave = hse.getMeanPassingScore()

        val records = hse.getRecords()

        System.out.println(hse.name)
        System.out.print("Количество бюджетных мест: ")
        System.out.println(hse.placesCount)
        System.out.print("Количество заявлений: ")
        System.out.println(records.size)
        System.out.print("Количество олимпиадников (БВИ): ")
        System.out.println(records count { it.isOlymp })
        System.out.print("Количество льготников: ")
        System.out.println(records count { it.isExempt })
        System.out.print("Количество целевиков: ")
        System.out.println(records count { it.isTargeted })
        System.out.print("Средний балл абитуриентов: ")
        System.out.println(java.lang.String.format("%.2f", hse.getAverageScore()))
        System.out.print("Среднеквадратичное отклонение: ")
        System.out.println((java.lang.String.format("%.2f", hse.getStandardDeviation())))
        System.out.print("Ожидаемый проходной балл первой волны: ")
        System.out.println(firstWave)
        System.out.print("Вероятный проходной балл второй волны: ")
        System.out.println(meanSecondWave)
        System.out.print("Минимально теоретически возможный проходной балл второй волны: ")
        System.out.println(minSecondWave)
        System.out.println()
    }
}