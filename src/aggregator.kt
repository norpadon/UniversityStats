package aggregators

import java.util.Random

public class Record (val Name : String, val Score : Int, val olymp : Boolean = false, val Priority : Int = 0) {

}

public trait Aggregator {
    fun getRecords() : List<Record>

    val Name : String
    val PlacesCount : Int
    val Reserve : Int

    fun getAverageScore() : Double {
        val rs = getRecords()
        return rs.fold(0) { (a, b) -> a + b.Score } / rs.size.toDouble()
    }

    fun getStandartDeviation() : Double {
        val rs = getRecords()
        val avg = getAverageScore()
        return Math.sqrt(rs.fold(0.0) { (a, b) -> a + (avg - b.Score) * (avg - b.Score) } / rs.size.toDouble())
    }

    fun getMaxMinScore() : Int {
        return gms(getRecords())
    }

    fun getMinMinScore() : Int {
        return gms(getRecords() filter { it.Priority == 0 })
    }

    fun getRealMinScore() : Int {
        return gms(getRecords() filter { it.Priority == 0 || Random().nextInt() % 3 == 0 })
    }

    private fun gms(data : List<Record>) : Int {
        if (PlacesCount == 0) {
            return Integer.MAX_VALUE
        }
        val sorted = (data sortDescendingBy { it.Score + if (it.olymp) 400 else 0 }).toArrayList()
        val olympcnt = data count { it.olymp }
        var r = Math.min(sorted.size, Math.max(PlacesCount, olympcnt + Reserve)) - 1
        return sorted[r].Score
    }
}