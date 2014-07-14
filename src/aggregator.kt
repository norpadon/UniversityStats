package aggregators

import java.util.Random

/*
 * Класс записи в таблице
 */
class Record (
        val name: String,
        val score: Int,
        val isOlymp: Boolean = false,
        val priority: Int = 0,
        val isTargeted: Boolean = false,
        val isExempt: Boolean = false
)

// Просто для удобства.
fun square(value: Int): Int = value * value
fun square(value: Double): Double = value * value

/*
 * Aggregator осуществляет получение данных по направлению подготовки
 *  и исполльзуется для рассчета конкурсной статистики.
 */
trait Aggregator {

    fun getRecords(): List<Record> // Возвращает таблицу заявлений на данное направление

    val name: String       // Название направления подготовки.
    val placesCount: Int   // Количество бюджетных мест.
    val targeted: Int      // Из них целевиков.
    val exempts: Int       // Из них льготников.
    val reserve: Int       // Размер резерва на случай если олимпиадников больше чем мест.

    // Возвращает средний балл абитуриентов.
    fun getAverageScore() : Double {
        val records = getRecords()
        return records.fold(0) {(a, b) -> a + b.score } / records.size.toDouble()
    }

    // Возвращает среднеквадратичное отклонение балла.
    fun getStandardDeviation(): Double {
        val records = getRecords()
        val average = getAverageScore()
        return Math.sqrt(records.fold(0.0) {(a, b) -> a + square(average - b.score) } / records.size.toDouble())
    }

    // Возвращает максимально возможный проходной балл (проходной балл первой волны).
    fun getMaxPassingScore(): Int {
        return auxSelectiveScoreCalc(getRecords())
    }

    // Возвращает минимально возможный проходной балл (учитываются только оригиналы аттестатов).
    fun getMinPassingScore(): Int {
        return auxSelectiveScoreCalc(getRecords() filter { it.priority == 0 })
    }

    // Возвращает ожидаемый проходной балл, основанный на предположении о том что верятность поступления
    //  абитуриента с копией аттестата равна 1/3 (В среднем 3 топовых вуза на направление подготовки).
    fun getMeanPassingScore(): Int {
        return auxSelectiveScoreCalc(getRecords() filter { it.priority == 0 || Random().nextInt() % 3 == 0 })
    }

    // Вспомогательная функция для рассчета проходного балла по заданной выборке.
    private fun auxSelectiveScoreCalc(data: List<Record>): Int {
        // Костыль для тех направлений, где нет бюджетных мест (Проргамма по экономике ВШЭ и Лондонского университета).
        if (placesCount == 0) {
            return -1
        }

        // Считаем количество олимпиадников
        val olympCount = data count { it.isOlymp }

        // Переменные для хранения количества мест, оставшихся от льготников и целевиков
        var exemptsCoount = exempts
        var targetedCount = targeted

        // Получаем множество конкурсантов, отсортированных по убыванию балла ЕГЭ + БВИ,
        //  отчитываем льготников и целевиков в пределах квоты и сортируем полученный список снова.
        val sorted = data sortDescendingBy
        { it.score + if (it.isOlymp) 400 else 0 } map
        { Pair(it, it.isOlymp || it.isTargeted && targetedCount-- > 0 || it.isExempt && exemptsCoount-- > 0) } sortDescendingBy
        { it.first.score + if (it.second) 400 else 0 }

        // Находим последнего зачисленного с учетом резерва
        val lastSuccessfulGuy = Math.min(sorted.size, Math.max(placesCount, olympCount + reserve)) - 1
        // Возвращаем его балл
        return sorted[lastSuccessfulGuy].first.score
    }
}