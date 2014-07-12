package aggregators

import jxl.*
import java.net.*
import java.util.ArrayList
import java.util.regex.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.TreeMap
import java.util.concurrent.Executors
import sun.awt.Mutex
import java.util.concurrent.Future

// Страница со списком направлений подготовки.
private val progPage = BufferedReader(InputStreamReader(
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
private val exec = Executors.newCachedThreadPool()

/*
 * Обобщенный агрегатор для Высшей Школы Экономики.
 * Конструктор принимает адрес excel таблицы с данными
 */
class HseAggregator(programUrl : String) : Aggregator {
    // Получение  таблицы
    private val conn = URL(programUrl).openConnection() as HttpURLConnection
    private val dataStream = conn.getInputStream()
    private val ss = Workbook.getWorkbook(dataStream)!!.getSheet(0)!!
    {
        // Заполнение таблицы мест в случае, если она еще не заполнена.
        if (placesMap.size == 0) {
            loadPlacesCount()
        }
    }

    // Вспомогательная функция для получнения содержимого ячейки таблицы
    private fun cell(column : Int, row : Int) : String {
        return ss.getCell(column, row)!!.getContents()!!
    }

    // Название направления подготовки получаем из кавычек внутри ячейки A2.
    override val Name = cell(0, 1).split("\"")[1]

    // Получаем список абитуриентов.
    private val dataList = ArrayList<Record>();
    {
        // Если на направление нужно сдавать 3 экзамена, в ячейке L5 находится строка "Сумма баллов".
        val subjcount = if (cell(10, 4) == "Сумма баллов") 3 else 4
        // Таблица начинается с седьмой строки.
        var curRow = 6
        // Заполняем таблицу пока можем.
        while (curRow < ss.getRows() && cell(2, curRow) != "") {
            // Имя.
            val name = cell(2, curRow)
            // Буфер для хранения фигни.
            var scs: String
            // Буфер для хранения суммарного балла.
            var score = 0
            // Считаем суммарный балл.
            for (i in 7..6 + subjcount) {
                scs = cell(i, curRow)
                score += if (scs != "") Integer.parseInt(scs) else 0
            }
            // Считаем приоритет.
            val prior = if (cell(3, curRow) == "Подлинник") 0 else 1
            // Смотрим на олимпиады.
            val olymp = cell(4, curRow) != ""
            // Сохраням полученные данные.
            dataList.add(Record(name, score, olymp, prior))
            ++curRow
        }
    }

    //Тут все ясно.
    override val PlacesCount = placesMap[Name]!!
    override val Exempts = exemptsMap[Name]!!
    override val Targeted = targetedMap[Name]!!
    override val Reserve = 25

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

    val m = regex.matcher(placesPage)
    while (m.find()) {
        placesMap.set(m.group(1)!!, Integer.parseInt(m.group(5)!!));
        targetedMap.set(m.group(1)!!, if (m.group(7)!! != "") Integer.parseInt(m.group(7)!!) else 0);
        exemptsMap.set(m.group(1)!!, if (m.group(9)!! != "") Integer.parseInt(m.group(9)!!) else 0);
    }

    placesMap.set("Программа двух дипломов по экономике НИУ ВШЭ и Лондонского университета", 0)
    targetedMap.set("Программа двух дипломов по экономике НИУ ВШЭ и Лондонского университета", 0)
    exemptsMap.set("Программа двух дипломов по экономике НИУ ВШЭ и Лондонского университета", 0)
}

// Возвращает аггрегаторы для всех направлений подготовки ВШЭ.
private fun getAllHseAggregators() : List<HseAggregator> {

    // Страшное регулярное выражение для поиска ссылок на excel файлы.
    val regex = Pattern.compile("""href="([^"]*.xls)"""")
    val m = regex.matcher(progPage)

    // Как ни странно, результат.
    val result = ArrayList<HseAggregator>();
    // Мьютекс для синхронизации доступа к массиву.
    val mutex = Mutex()
    // Список задач, завершения котрых нажно дождаться.
    val fs = ArrayList<Future<*>>()
    //Ищем ссылки
    while (m.find()) {
        val g = m.group(1)!!
        // И просим наш пул потоков их обработать.
        fs.add(exec.submit {
            val res = HseAggregator(g)
            // Вот это нужно чтобы избежать гонки данных при записи результата.
            mutex.lock()
            result.add(res)
            mutex.unlock()
        })
    }

    // Ждем завершения обработки.
    for (f in fs) {
        f.get()
    }

    // Возвращаем резульат.
    return result
}

// Печатает статистику.
fun printData() {
    val m = Mutex()
    val fs = ArrayList<Future<*>>()
    for (hse in getAllHseAggregators()) {
        fs.add(exec.submit() {
            val minmax = hse.getMaxPassingScore()
            val minmin = hse.getMinPassingScore()
            val avgmin = hse.getMeanPassingScore()

            val recs = hse.getRecords()

            m.lock()
            System.out.println(hse.Name)
            System.out.print("Количество бюджетных мест: ")
            System.out.println(hse.PlacesCount)
            System.out.print("Количество заявлений: ")
            System.out.println(recs.size)
            System.out.print("Из них олимпиадников: ")
            System.out.println(recs.filter { it.Olymp } .size)
            System.out.print("Средний балл абитуриентов: ")
            System.out.println(java.lang.String.format("%.2f", hse.getAverageScore()))
            System.out.print("Среднеквадратичное отклонение: ")
            System.out.println((java.lang.String.format("%.2f", hse.getStandardDeviation())))
            System.out.print("Ожидаемый проходной балл первой волны: ")
            System.out.println(minmax)
            System.out.print("Вероятный проходной балл второй волны: ")
            System.out.println(avgmin)
            System.out.print("Минимально теоретически возможный проходной балл второй волны: ")
            System.out.println(minmin)
            System.out.println()
            m.unlock()
        })
    }

    for (f in fs) {
        f.get()
    }
}