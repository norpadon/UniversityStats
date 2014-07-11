package aggregators

import jxl.*
import java.net.*
import java.io.BufferedInputStream
import java.util.ArrayList
import java.text.*
import java.util.regex.*
import sun.misc.IOUtils
import java.util.Scanner
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.TreeMap
import java.util.concurrent.Executors
import sun.awt.Mutex
import java.util.concurrent.TimeUnit
import java.util.concurrent.Future

private val progPage = BufferedReader(InputStreamReader(
        (URL("http://ba.hse.ru/base2014").openConnection() as HttpURLConnection).getInputStream()!!)).readText()
private val placesPage = BufferedReader(InputStreamReader(
        (URL("http://ba.hse.ru/kolmest2014").openConnection() as HttpURLConnection).getInputStream()!!)).readText()

private val placesMap = TreeMap<String, Int>()

private val exec = Executors.newCachedThreadPool()

class HseAggregator(programUrl : String) : Aggregator {
    val conn = URL(programUrl).openConnection() as HttpURLConnection
    val dataStream = conn.getInputStream()
    val ss = Workbook.getWorkbook(dataStream)!!.getSheet(0)!!

    private fun cell(column : Int, row : Int) : String {
        return ss.getCell(column, row)!!.getContents()!!
    }

    override val Name = cell(0, 1).split("\"")[1]

    val dataList = ArrayList<Record>();
    {
        val subjcount = if (cell(10, 4) == "Сумма баллов") 3 else 4
        var curRow = 6
        while (curRow < ss.getRows() && cell(2, curRow) != "") {
            val name = cell(2, curRow)
            var scs = ""
            var score = 0
            for (i in 7..6 + subjcount) {
                scs = cell(i, curRow)
                score += if (scs != "") Integer.parseInt(scs) else 0
            }
            val prior = if (cell(3, curRow) == "Подлинник") 0 else 1
            val olymp = cell(4, curRow) != ""
            dataList.add(Record(name, score, olymp, prior))
            ++curRow
        }
    }

    override val PlacesCount = placesMap[Name]!!
    override val Reserve = 25

    override fun getRecords() : List<Record> {
        return ArrayList<Record>(dataList)
    }
}

private fun loadPlacesCount() {
    val regex = Pattern.compile(
            """<td>\s*<a href="[^<>]*">([^<>]*)</a>((<br>)|[^<>])*</td>\s*<td style="[^<>]*">(\d+)</td>"""
    )

    val m = regex.matcher(placesPage)
    while (m.find()) {
        placesMap.set(m.group(1)!!, Integer.parseInt(m.group(4)!!));
    }

    placesMap.set("Программа двух дипломов по экономике НИУ ВШЭ и Лондонского университета", 0)
}

private fun getAllHseAggregators() : List<HseAggregator> {
    loadPlacesCount()

    val regex = Pattern.compile("""href="([^"]*.xls)"""")
    val m = regex.matcher(progPage)

    val result = ArrayList<HseAggregator>();
    val mutex = Mutex()
    val fs = ArrayList<Future<*>>()
    while (m.find()) {
        val g = m.group(1)!!
        fs.add(exec.submit {
            val res = HseAggregator(g)
            mutex.lock()
            result.add(res)
            mutex.unlock()
        })
    }

    for (f in fs) {
        f.get()
    }

    return result
}

fun printData() {
    val m = Mutex()
    val fs = ArrayList<Future<*>>()
    for (hse in getAllHseAggregators()) {
        fs.add(exec.submit() {
            val minmax = hse.getMaxMinScore()
            val minmin = hse.getMinMinScore()
            val avgmin = hse.getRealMinScore()

            val recs = hse.getRecords()

            m.lock()
            System.out.println(hse.Name)
            System.out.print("Количество бюджетных мест: ")
            System.out.println(hse.PlacesCount)
            System.out.print("Количество заявлений: ")
            System.out.println(recs.size)
            System.out.print("Из них олимпиадников: ")
            System.out.println(recs.filter { it.olymp } .size)
            System.out.print("Средний балл абитуриентов: ")
            System.out.println(java.lang.String.format("%.2f", hse.getAverageScore()))
            System.out.print("Среднеквадратичное отклонение: ")
            System.out.println((java.lang.String.format("%.2f", hse.getStandartDeviation())))
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