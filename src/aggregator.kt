package aggregators

import java.util.Random

/*
 * Класс записи в таблице
 */
public class Record (val Name: String, val Score: Int,
                     val Olymp: Boolean = false, val Priority: Int = 0,
                     val Targeted: Boolean = false, val Exempt: Boolean = false) {

}

/*
 * Aggregator осуществляет получение данных по направлению подготовки
 *  и исполльзуется для рассчета конкурсной статистики.
 */
public trait Aggregator {

    fun getRecords(): List<Record> // Возвращает таблицу заявлений на данное направление

    val Name: String       // Название направления подготовки.
    val PlacesCount: Int   // Количество бюджетных мест.
    val Targeted: Int      // Из них целевиков.
    val Exempts: Int       // Из них льготников.
    val Reserve: Int       // Размер резерва на случай если олимпиадников больше чем мест.

    // Возвращает средний балл абитуриентов.
    fun getAverageScore() : Double {
        val rs = getRecords()
        return rs.fold(0) { (a, b) -> a + b.Score } / rs.size.toDouble()
    }

    // Возвращает среднеквадратичное отклонение балла.
    fun getStandardDeviation(): Double {
        val rs = getRecords()
        val avg = getAverageScore()
        return Math.sqrt(rs.fold(0.0) { (a, b) -> a + (avg - b.Score) * (avg - b.Score) } / rs.size.toDouble())
    }

    // Возвращает максимально возможный проходной балл (проходной балл первой волны).
    fun getMaxPassingScore(): Int {
        return auxSelectiveScoreCalc(getRecords())
    }

    // Возвращает минимально возможный проходной балл (учитываются только оригиналы аттестатов).
    fun getMinPassingScore(): Int {
        return auxSelectiveScoreCalc(getRecords() filter { it.Priority == 0 })
    }

    // Возвращает ожидаемый проходной балл, основанный на предположении о том что верятность поступления
    //  абитуриента с копией аттестата равна 1/3 (В среднем 3 топовых вуза на направление подготовки).
    fun getMeanPassingScore(): Int {
        return auxSelectiveScoreCalc(getRecords() filter { it.Priority == 0 || Random().nextInt() % 3 == 0 })
    }

    // Вспомогательная функция для рассчета проходного балла по заданной выборке.
    private fun auxSelectiveScoreCalc(data: List<Record>): Int {
        // Костыль для тех направлений, где нет бюджетных мест (Проргамма по экономике ВШЭ и Лондонского университета).
        if (PlacesCount == 0) {
            return -1
        }

        // Считаем количество олимпиадников
        val olympcnt = data count { it.Olymp }

        // Переменные для хранения количества мест, оставшихся от льготников и целевиков
        var exemptcnt = Exempts
        var targetedcnt = Targeted

        // Получаем множество конкурсантов, отсортированных по убыванию балла ЕГЭ + БВИ,
        //  отчитываем льготников и целевиков в пределах квоты и сортируем полученный список снова.
        val sorted = data sortDescendingBy
        { it.Score + if (it.Olymp) 400 else 0 } map
        { Pair(it, it.Olymp || it.Targeted && targetedcnt-- > 0 || it.Exempt && exemptcnt-- > 0) } sortDescendingBy
        { it.first.Score + if (it.second) 400 else 0 }
        // Находим последнего зачисленного с учетом резерва
        var r = Math.min(sorted.size, Math.max(PlacesCount, olympcnt + Reserve)) - 1
        // Возвращаем его балл
        return sorted[r].first.Score
    }
}